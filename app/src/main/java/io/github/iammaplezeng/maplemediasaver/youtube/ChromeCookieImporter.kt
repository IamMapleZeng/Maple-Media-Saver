package io.github.iammaplezeng.maplemediasaver.youtube

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal enum class ChromeImportError {
    ROOT_DENIED,
    CHROME_NOT_FOUND,
    NO_COOKIES,
    FAILED
}

internal class ChromeImportException(val reason: ChromeImportError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: reason.name, cause)

internal object ChromeCookieImporter {
    private const val CHROME_EPOCH_OFFSET_SECONDS = 11_644_473_600L
    private val CHROME_PACKAGES = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary"
    )
    private val PRIORITY_NAMES = setOf(
        "SID", "HSID", "SSID", "APISID", "SAPISID",
        "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID",
        "__Secure-1PSIDTS", "__Secure-3PSIDTS", "__Secure-1PSIDCC", "__Secure-3PSIDCC",
        "LOGIN_INFO", "VISITOR_INFO1_LIVE", "VISITOR_PRIVACY_METADATA", "PREF", "SOCS", "CONSENT", "YSC"
    )

    private val v10Key: SecretKeySpec by lazy {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec("peanuts".toCharArray(), "saltysalt".toByteArray(Charsets.UTF_8), 1, 128)
        SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
    private val v10Iv = IvParameterSpec(ByteArray(16) { 0x20 })

    fun importFromChrome(context: Context): Int {
        val tempDir = File(context.cacheDir, "chrome-cookies-${UUID.randomUUID()}")
        if (!tempDir.mkdirs()) throw ChromeImportException(ChromeImportError.FAILED, "Cannot create temp dir")
        try {
            val copiedDb = copyChromeCookiesViaRoot(tempDir)
            val lines = readYouTubeCookies(copiedDb)
            if (lines.isEmpty()) throw ChromeImportException(ChromeImportError.NO_COOKIES)
            val limited = if (lines.size <= 256) lines else {
                lines.sortedByDescending { it.priority }.take(256)
            }
            val netscape = "# Netscape HTTP Cookie File\n" + limited.joinToString("\n", postfix = "\n") { it.line }
            YouTubeCookies.saveSanitized(context.applicationContext, netscape)
            return limited.size
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private val ROOT_MANAGER_PACKAGES = listOf(
        "com.sukisu.ultra",
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext",
        "me.bmax.apatch",
        "com.topjohnwu.magisk"
    )
    private val SU_CANDIDATES = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su"
    )

    fun rootManagerLaunchIntent(context: Context): android.content.Intent? {
        val pm = context.packageManager
        for (pkg in ROOT_MANAGER_PACKAGES) {
            pm.getLaunchIntentForPackage(pkg)?.let { return it }
        }
        return null
    }

    private fun copyChromeCookiesViaRoot(tempDir: File): File {
        val uid = Process.myUid()
        val destPath = tempDir.absolutePath
        val candidates = CHROME_PACKAGES.flatMap { pkg ->
            listOf(
                "/data/data/$pkg/app_chrome/Default/Cookies",
                "/data/data/$pkg/app_chrome/Default/Network/Cookies",
                "/data/user/0/$pkg/app_chrome/Default/Cookies",
                "/data/user/0/$pkg/app_chrome/Default/Network/Cookies"
            )
        }.joinToString(" ")
        val pkgRoots = CHROME_PACKAGES.flatMap { pkg ->
            listOf("/data/user/0/$pkg", "/data/data/$pkg")
        }.joinToString(" ")
        val script = """
            SRC=""
            for p in $candidates; do
              if [ -f "${'$'}p" ]; then
                SRC="${'$'}p"
                break
              fi
            done
            if [ -z "${'$'}SRC" ]; then
              for r in $pkgRoots; do
                if [ -d "${'$'}r" ]; then
                  FOUND=${'$'}(find "${'$'}r" -maxdepth 6 -type f -name "Cookies" 2>/dev/null | head -n 1)
                  if [ -n "${'$'}FOUND" ]; then
                    SRC="${'$'}FOUND"
                    break
                  fi
                fi
              done
            fi
            if [ -z "${'$'}SRC" ]; then
              echo "XTG_CHROME_NOT_FOUND"
              id 2>&1
              ls -la /data/user/0/com.android.chrome 2>&1 | head -n 20
              exit 2
            fi
            echo "XTG_CHROME_SRC:${'$'}SRC"
            cp "${'$'}SRC" "$destPath/Cookies" || exit 3
            [ -f "${'$'}SRC-wal" ] && cp "${'$'}SRC-wal" "$destPath/Cookies-wal" 2>/dev/null || true
            [ -f "${'$'}SRC-shm" ] && cp "${'$'}SRC-shm" "$destPath/Cookies-shm" 2>/dev/null || true
            chown $uid:$uid "$destPath"/Cookies* 2>/dev/null || true
            chmod 600 "$destPath"/Cookies* 2>/dev/null || chmod 644 "$destPath"/Cookies* 2>/dev/null || true
            echo "XTG_CHROME_OK"
        """.trimIndent()

        var process: java.lang.Process? = null
        var lastError: Exception? = null
        val commands = SU_CANDIDATES.flatMap { suBin ->
            listOf(listOf(suBin, "-mm", "-c", script), listOf(suBin, "-c", script))
        }
        var output = ""
        var code = -1
        for (cmd in commands) {
            try {
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().use { it.readText() }
                val finished = p.waitFor(15, TimeUnit.SECONDS)
                if (!finished) {
                    p.destroyForcibly()
                    throw ChromeImportException(ChromeImportError.FAILED, "su timed out")
                }
                val exit = p.exitValue()
                // If -mm is rejected as an invalid flag, try the next candidate
                if (cmd.contains("-mm") && exit != 0 && "XTG_CHROME_" !in out) {
                    continue
                }
                process = p
                output = out
                code = exit
                break
            } catch (error: ChromeImportException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        if (process == null) {
            android.util.Log.w("XTGMediaSaver", "[CHROME] su unavailable: ${lastError?.message}")
            throw ChromeImportException(ChromeImportError.ROOT_DENIED, "su unavailable", lastError)
        }
        if (code == 2 || "XTG_CHROME_NOT_FOUND" in output) {
            android.util.Log.w("XTGMediaSaver", "[CHROME] db not found: ${output.take(500)}")
            throw ChromeImportException(ChromeImportError.CHROME_NOT_FOUND)
        }
        val dbFile = File(tempDir, "Cookies")
        if (code != 0 || !dbFile.isFile || dbFile.length() <= 0L) {
            android.util.Log.w("XTGMediaSaver", "[CHROME] su exit=$code out=${output.take(160)}")
            if ("XTG_CHROME_OK" !in output) {
                throw ChromeImportException(ChromeImportError.ROOT_DENIED, "su exit=$code")
            }
            throw ChromeImportException(ChromeImportError.FAILED, "Cookies file unreadable")
        }
        return dbFile
    }

    private data class CookieEntry(val line: String, val priority: Int)

    private fun readYouTubeCookies(dbFile: File): List<CookieEntry> {
        val entries = ArrayList<CookieEntry>()
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val metaVersion = runCatching {
                db.rawQuery("SELECT value FROM meta WHERE key = 'version'", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.toIntOrNull() ?: 0 else 0
                }
            }.getOrDefault(0)

            val query = """
                SELECT host_key, path, is_secure, expires_utc, name, value, encrypted_value, is_httponly
                FROM cookies
                WHERE host_key = 'youtube.com' OR host_key = '.youtube.com' OR host_key LIKE '%.youtube.com'
            """.trimIndent()

            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val hostKey = cursor.getString(0)?.trim().orEmpty()
                    val path = cursor.getString(1)?.ifBlank { "/" } ?: "/"
                    val isSecure = cursor.getInt(2) != 0
                    val expiresUtc = cursor.getLong(3)
                    val name = cursor.getString(4)?.trim().orEmpty()
                    val plainValue = cursor.getString(5).orEmpty()
                    val encryptedValue = cursor.getBlob(6)
                    val isHttpOnly = cursor.getInt(7) != 0

                    val value = plainValue.ifEmpty {
                        decryptCookieValue(encryptedValue, hostKey, metaVersion)
                    }
                    if (hostKey.isEmpty() || name.isEmpty() || value.isEmpty()) continue
                    if (hasControlChars(hostKey) || hasControlChars(path) || hasControlChars(name) || hasControlChars(value)) continue

                    val line = formatNetscapeLine(hostKey, path, isSecure, expiresUtc, name, value, isHttpOnly)
                    val priority = if (name in PRIORITY_NAMES) 1 else 0
                    entries.add(CookieEntry(line, priority))
                }
            }
        } finally {
            db.close()
        }
        return entries
    }

    internal fun formatNetscapeLine(
        hostKey: String,
        path: String,
        isSecure: Boolean,
        expiresUtc: Long,
        name: String,
        value: String,
        isHttpOnly: Boolean
    ): String {
        val prefix = if (isHttpOnly) "#HttpOnly_" else ""
        val includeSubdomains = if (hostKey.startsWith(".")) "TRUE" else "FALSE"
        val secure = if (isSecure) "TRUE" else "FALSE"
        val expires = convertChromeExpiry(expiresUtc)
        val safePath = if (path.startsWith("/")) path else "/$path"
        return "$prefix$hostKey\t$includeSubdomains\t$safePath\t$secure\t$expires\t$name\t$value"
    }

    internal fun convertChromeExpiry(expiresUtc: Long): Long {
        if (expiresUtc <= 0L) return 0L
        return ((expiresUtc / 1_000_000L) - CHROME_EPOCH_OFFSET_SECONDS).coerceAtLeast(0L)
    }

    internal fun decryptCookieValue(encrypted: ByteArray?, hostKey: String, metaVersion: Int): String {
        if (encrypted == null || encrypted.isEmpty()) return ""
        val hasPrefix = encrypted.size > 3 &&
            encrypted[0] == 'v'.code.toByte() &&
            encrypted[1] == '1'.code.toByte() &&
            (encrypted[2] == '0'.code.toByte() || encrypted[2] == '1'.code.toByte())
        if (!hasPrefix) {
            return String(encrypted, Charsets.UTF_8)
        }
        return runCatching {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, v10Key, v10Iv)
            val decrypted = cipher.doFinal(encrypted, 3, encrypted.size - 3)
            val payload = if (metaVersion >= 24 && decrypted.size > 32 && matchesHostHash(decrypted, hostKey)) {
                decrypted.copyOfRange(32, decrypted.size)
            } else {
                decrypted
            }
            String(payload, Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun matchesHostHash(decrypted: ByteArray, hostKey: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(hostKey.toByteArray(Charsets.UTF_8))
        for (i in digest.indices) {
            if (decrypted[i] != digest[i]) return false
        }
        return true
    }

    private fun hasControlChars(text: String): Boolean = text.any { it.code < 32 || it.code == 127 }
}
