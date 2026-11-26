package io.github.iammaplezeng.xtgmediasaver.youtube

import android.os.Bundle
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.iammaplezeng.xtgmediasaver.XTGMediaSaverModule
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Version-specific, read-only inspection of the host's own player response. No credentials cross processes. */
internal object NativePlaybackCapture {
    private data class Entry(val manifest: VideoManifest, val capturedAt: Long)

    private val recent = LinkedHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<Bundle?>>()
    private val vrRequests: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))
    private val hostExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xtg-yt-host-resolve").apply { isDaemon = true }
    }

    @Volatile private var playerResponseService: WeakReference<Any>? = null
    @Volatile private var playerServiceRef: WeakReference<Any>? = null
    @Volatile private var anyRpcServiceRef: WeakReference<Any>? = null
    @Volatile private var hostLoader: WeakReference<ClassLoader>? = null
    @Volatile var lastVisitorData: String? = null
        private set

    @Synchronized fun forVideo(link: String): Bundle? {
        val canonical = YouTubeLink.parse(link) ?: return null
        val entry = recent[canonical] ?: return null
        if (SystemClock.elapsedRealtime() - entry.capturedAt !in 0..MAX_AGE) return null
        return NativePlaybackPayload.encode(entry.manifest)
    }

    @Synchronized fun hasAdaptiveChoices(link: String): Boolean {
        val canonical = YouTubeLink.parse(link) ?: return false
        val entry = recent[canonical] ?: return false
        if (SystemClock.elapsedRealtime() - entry.capturedAt !in 0..MAX_AGE) return false
        return entry.manifest.choices.any { it.audioUrl != null } || entry.manifest.choices.size > 1
    }

    fun prefetchInHost(link: String): CompletableFuture<Bundle?> {
        val canonical = YouTubeLink.parse(link) ?: return CompletableFuture.completedFuture(null)
        if (hasAdaptiveChoices(canonical)) {
            return CompletableFuture.completedFuture(forVideo(canonical))
        }
        val videoId = canonical.substringAfter("v=")
        val pending = inFlight.computeIfAbsent(canonical) {
            CompletableFuture.supplyAsync({
                try {
                    fetchAdaptiveViaHost(canonical, videoId)
                } catch (error: Throwable) {
                    XTGMediaSaverModule.log("[YT] host adaptive resolve failed ${error.javaClass.simpleName}")
                }
                forVideo(canonical)
            }, hostExecutor)
        }
        pending.whenComplete { _, _ -> inFlight.remove(canonical, pending) }
        return pending
    }

    fun resolveAsyncForTap(link: String, timeoutMs: Long, callback: (Bundle?) -> Unit) {
        val future = prefetchInHost(link)
        Thread({
            val result = try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {
                forVideo(link)
            }
            callback(result ?: forVideo(link))
        }, "xtg-yt-tap-wait").apply { isDaemon = true }.start()
    }

    fun install(loader: ClassLoader) {
        hostLoader = WeakReference(loader)
        val response = Class.forName("bhsp", false, loader)
        val streamingDataClass = Class.forName("com.google.protos.youtube.api.innertube.StreamingDataOuterClass\$StreamingData", false, loader)
        val detailsClass = Class.forName("bhst", false, loader)
        val builder = Class.forName("allq", false, loader)

        XposedBridge.hookMethod(builder.getDeclaredConstructor(response), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val player = param.args[0] ?: return
                    recordFromPlayer(player)
                } catch (error: Throwable) {
                    XTGMediaSaverModule.log("[YT] native player inspection skipped ${error.javaClass.simpleName}")
                }
            }
        })

        XposedBridge.hookMethod(builder.getDeclaredConstructor(streamingDataClass, detailsClass), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val streams = param.args[0] ?: return
                    val details = param.args[1] ?: return
                    recordFromStreams(details, streams)
                } catch (error: Throwable) {
                    XTGMediaSaverModule.log("[YT] native onesie inspection skipped ${error.javaClass.simpleName}")
                }
            }
        })

        runCatching { installHostServiceHooks(loader) }
            .onFailure { XTGMediaSaverModule.log("[YT] host service hooks skipped ${it.javaClass.simpleName}") }

        XTGMediaSaverModule.log("[YT] native player observer installed")
    }

    private fun installHostServiceHooks(loader: ClassLoader) {
        verifyClientCodec(loader)
        Class.forName("asqj", false, loader).declaredConstructors.forEach { ctor ->
            XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let { playerResponseService = WeakReference(it) }
                }
            })
        }

        Class.forName("asry", false, loader).declaredConstructors.forEach { ctor ->
            XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let { playerServiceRef = WeakReference(it) }
                }
            })
        }

        Class.forName("alql", false, loader).declaredConstructors.forEach { ctor ->
            XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val self = param.thisObject ?: return
                    if (runCatching { getField(self, "a") }.getOrNull() != null) {
                        anyRpcServiceRef = WeakReference(self)
                    }
                }
            })
        }

        val serializerClass = Class.forName("alpc", false, loader)
        val buildProtoMethod = serializerClass.declaredMethods.firstOrNull { it.name == "a" && it.parameterCount == 1 }
        if (buildProtoMethod != null) {
            XposedBridge.hookMethod(buildProtoMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val req = runCatching { getField(param.thisObject, "a") }.getOrNull() ?: return
                    if (!vrRequests.contains(req)) return
                    val original = param.result ?: return
                    runCatching { rewriteRequestAsVr(original, loader) }
                        .onSuccess {
                            param.result = it
                            XTGMediaSaverModule.log("[YT] alternate request serialized: type=${original.javaClass.simpleName} changed=${it !== original}")
                        }
                        .onFailure { XTGMediaSaverModule.log("[YT] vr proto rewrite skipped ${it.javaClass.simpleName}") }
                }
            })
        }

        val headersMethod = serializerClass.getDeclaredMethod("m")
        XposedBridge.hookMethod(headersMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val req = runCatching { getField(param.thisObject, "a") }.getOrNull() ?: return
                if (!vrRequests.contains(req)) return
                try {
                    @Suppress("UNCHECKED_CAST")
                    val original = param.result as? Map<String, String> ?: return
                    param.result = HashMap(original).apply {
                        put("User-Agent", YouTubeNetwork.VR_USER_AGENT)
                        remove("X-Youtube-Cold-Config-Data")
                        remove("X-Youtube-Cold-Hash-Data")
                        remove("X-Youtube-Hot-Hash-Data")
                    }
                } catch (error: Throwable) {
                    XTGMediaSaverModule.log("[YT] alternate headers skipped ${error.javaClass.simpleName}")
                }
            }
        })
    }

    private fun verifyClientCodec(loader: ClassLoader) {
        val type = Class.forName("com.google.protos.youtube.api.innertube.InnertubeContext\$ClientInfo", false, loader)
        val default = type.getDeclaredField("a").apply { isAccessible = true }.get(null)
        val base = Class.forName("bcbp", false, loader)
        val parser = base.getDeclaredMethod("parseFrom", base, ByteArray::class.java).apply { isAccessible = true }
        val sample = parser.invoke(null, default, buildVrClientInfoBytes("en", "US", "xtg-contract-probe")) ?: error("Missing client proto")
        check(getField(sample, "s") == 28 && getField(sample, "u") == "1.60.19" && getField(sample, "n") == "xtg-contract-probe")
        XTGMediaSaverModule.log("[YT] alternate client serializer contract verified")
    }

    private fun fetchAdaptiveViaHost(canonical: String, videoId: String) {
        val svc = playerResponseService?.get()
        val playerService = svc?.let { getField(it, "b") } ?: playerServiceRef?.get()
        val rpcBase = svc ?: anyRpcServiceRef?.get()
        val loader = hostLoader?.get()
        if (playerService == null || rpcBase == null || loader == null) {
            XTGMediaSaverModule.log("[YT] host services not ready: svc=${svc != null} ps=${playerService != null} rpc=${rpcBase != null}")
            return
        }

        executeHostPlayerRequest(playerService, rpcBase, loader, videoId, useVr = false)
        // Legacy VR direct formats are no longer reliable; do not repeatedly request them.
        // The companion resolver tries current public client profiles if the host supplies SABR only.
    }

    private fun executeHostPlayerRequest(
        playerService: Any,
        rpcBase: Any,
        loader: ClassLoader,
        videoId: String,
        useVr: Boolean
    ) {
        val req = invoke(playerService, "e") ?: return
        setField(req, "a", videoId)
        setField(req, "f", true)
        setField(req, "g", true)
        setField(req, "l", true)
        setField(req, "j", ByteArray(0))
        // alna.C(): cache reads are enabled for policy 3; D() also controls cache writes.
        // Policy 1 disables both for this module-created request, leaving host caches intact.
        setField(req, "C", 1)
        invoke(req, "z")
        if (useVr) vrRequests.add(req)
        try {
            val alom = getField(playerService, "a") ?: return
            val bhspDefault = Class.forName("bhsp", false, loader).getDeclaredField("a").apply { isAccessible = true }.get(null)
            val aqba = Class.forName("aqba", false, loader).getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(12)
            val asrp = Class.forName("asrp", false, loader).getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(2)
            val executor = invoke(rpcBase, "i", bhspDefault, alom, aqba, asrp, false) ?: return
            val future = invoke(executor, "c", req) as? Future<*> ?: return
            val player = try {
                future.get(3_000L, TimeUnit.MILLISECONDS)
            } catch (timeout: Throwable) {
                future.cancel(true)
                throw timeout
            }

            if (player != null) {
                val statusObj = getField(player, "f")
                val statusCode = (statusObj?.let { getField(it, "c") } as? Int) ?: 0
                val statusReason = (statusObj?.let { getField(it, "e") } as? String).orEmpty()
                XTGMediaSaverModule.log("[YT] host player fetched (vr=$useVr): status=$statusCode reason=${statusReason.take(80)}")
                recordFromPlayer(player)
            } else {
                XTGMediaSaverModule.log("[YT] host player returned null (vr=$useVr)")
            }
        } catch (error: Throwable) {
            val root = generateSequence(error) { it.cause }.last()
            val status = runCatching { getField(root, "b")?.let { getField(it, "d") } as? Int }.getOrNull()
            XTGMediaSaverModule.log("[YT] host player call error (vr=$useVr): ${root.javaClass.simpleName} http=${status ?: "unknown"}")
        } finally {
            if (useVr) vrRequests.remove(req)
        }
    }

    private fun rewriteRequestAsVr(bhsm: Any, loader: ClassLoader): Any {
        val origCtx = getField(bhsm, "c") ?: return bhsm
        val origClient = getField(origCtx, "c")
        val hl = (origClient?.let { getField(it, "g") } as? String)?.ifBlank { "en" } ?: "en"
        val gl = (origClient?.let { getField(it, "j") } as? String)?.ifBlank { "US" } ?: "US"

        val clientInfoClass = Class.forName(
            "com.google.protos.youtube.api.innertube.InnertubeContext\$ClientInfo",
            false,
            loader
        )
        val defaultClientInfo = clientInfoClass.getDeclaredField("a").apply { isAccessible = true }.get(null)
        val bcbpClass = Class.forName("bcbp", false, loader)
        val parseFrom = bcbpClass.getDeclaredMethod("parseFrom", bcbpClass, ByteArray::class.java).apply { isAccessible = true }
        val vrClient = parseFrom.invoke(null, defaultClientInfo, buildVrClientInfoBytes(hl, gl)) ?: return bhsm

        val ctxBuilder = invoke(origCtx, "toBuilder") ?: return bhsm
        invoke(ctxBuilder, "copyOnWrite")
        val ctxInstance = getField(ctxBuilder, "instance") ?: return bhsm
        setField(ctxInstance, "c", vrClient)
        setField(ctxInstance, "b", ((getField(ctxInstance, "b") as? Int) ?: 0) or 1)
        val rebuiltCtx = invoke(ctxBuilder, "build") ?: return bhsm

        val reqBuilder = invoke(bhsm, "toBuilder") ?: return bhsm
        invoke(reqBuilder, "copyOnWrite")
        val reqInstance = getField(reqBuilder, "instance") ?: return bhsm
        setField(reqInstance, "c", rebuiltCtx)
        setField(reqInstance, "b", ((getField(reqInstance, "b") as? Int) ?: 0) or 1)
        setField(reqInstance, "q", null)
        setField(reqInstance, "b", ((getField(reqInstance, "b") as? Int) ?: 0) and 2097152.inv())
        return invoke(reqBuilder, "build") ?: bhsm
    }

    internal fun buildVrClientInfoBytes(hl: String, gl: String, rolloutToken: String = ""): ByteArray {
        val out = ByteArrayOutputStream(128)
        writeProtoString(out, 1, hl)
        writeProtoString(out, 2, gl)
        writeProtoString(out, 12, "Oculus")
        writeProtoString(out, 13, "Quest 3")
        writeProtoVarintField(out, 16, 28) // ANDROID_VR
        writeProtoString(out, 17, "1.60.19")
        writeProtoString(out, 18, "Android")
        writeProtoString(out, 19, "12L")
        writeProtoVarintField(out, 64, 32)
        if (rolloutToken.isNotEmpty()) {
            // Verified on 21.47.483: ClientInfo.n is string field 108; 109 is a nested message.
            writeProtoString(out, 108, rolloutToken)
        }
        return out.toByteArray()
    }

    private fun writeProtoVarintField(out: ByteArrayOutputStream, fieldNumber: Int, value: Int) {
        writeRawVarint(out, (fieldNumber shl 3) or 0)
        writeRawVarint(out, value)
    }

    private fun writeProtoString(out: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeRawVarint(out, (fieldNumber shl 3) or 2)
        writeRawVarint(out, bytes.size)
        out.write(bytes)
    }

    private fun writeRawVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    private fun recordFromPlayer(player: Any) {
        val respCtx = runCatching { getField(player, "d") }.getOrNull()
        val vData = (respCtx?.let { runCatching { getField(it, "c") }.getOrNull() } as? String)?.takeIf { it.length in 8..512 }
        if (vData != null) lastVisitorData = vData
        val status = getField(player, "f")
        if (status != null && getField(status, "c") != 0) return
        val details = getField(player, "g") ?: return
        val streams = getField(player, "h") ?: return
        recordFromStreams(details, streams)
    }

    private fun recordFromStreams(details: Any, streams: Any) {
        val id = (getField(details, "c") as? String)?.takeIf { it.isNotEmpty() } ?: return
        val canonical = YouTubeLink.parse("https://www.youtube.com/watch?v=$id") ?: return
        // In bhst (VideoDetails proto): f (tag 17) = isLive, h (tag 27) = isPrivate, q (tag 43) = isLiveContent.
        val isLive = getField(details, "f") == true || getField(details, "q") == true ||
            ((getField(streams, "i") as? String)?.isNotEmpty() == true)
        val isPrivate = getField(details, "h") == true

        val progressive = items(streams, "e")
        val adaptive = items(streams, "f")
        val hasDrm = (progressive + adaptive).any { (getField(it, "s") as? Collection<*>)?.isNotEmpty() == true }
        if (isLive || isPrivate || hasDrm) {
            synchronized(this@NativePlaybackCapture) { recent.remove(canonical) }
            return
        }

        val audio = adaptive
            .filter { mime(it).startsWith("audio/mp4") && mime(it).contains("mp4a", true) }
            .filter { validUrl(it) != null }
            .maxByOrNull { (getField(it, "h") as? Int) ?: 0 }
            ?.let(::validUrl)

        data class Candidate(val choice: VideoChoice, val bitrate: Int)
        val choices = (progressive + adaptive).mapNotNull { format ->
            val mime = mime(format)
            val height = (getField(format, "k") as? Int) ?: 0
            val bitrate = (getField(format, "h") as? Int) ?: 0
            val url = validUrl(format) ?: return@mapNotNull null
            val separate = progressive.none { it === format }
            if (!mime.startsWith("video/mp4") || !mime.contains("avc", true) || height !in 1..4320 || (separate && audio == null)) {
                return@mapNotNull null
            }
            Candidate(VideoChoice("${height}p · MP4", height, url, if (separate) audio else null), bitrate)
        }.sortedWith(
            compareByDescending<Candidate> { it.choice.height }
                .thenBy { it.choice.audioUrl != null }
                .thenByDescending { it.bitrate }
        ).map { it.choice }.distinctBy { it.height }

        if (choices.isNotEmpty()) {
            val title = (getField(details, "d") as? String).orEmpty().take(500)
            synchronized(this@NativePlaybackCapture) {
                val now = SystemClock.elapsedRealtime()
                val existing = recent[canonical]?.takeIf { now - it.capturedAt in 0..MAX_AGE }?.manifest
                val mergedChoices = if (existing != null) {
                    (choices + existing.choices)
                        .sortedWith(compareByDescending<VideoChoice> { it.height }.thenBy { it.audioUrl != null })
                        .distinctBy { it.height }
                } else choices
                val mergedTitle = title.ifBlank { existing?.title.orEmpty() }
                recent[canonical] = Entry(VideoManifest(canonical, mergedTitle, mergedChoices.take(20)), now)
                while (recent.size > 12) recent.remove(recent.keys.first())
            }
        }
        val adaptiveUrls = adaptive.count { validUrl(it) != null }
        XTGMediaSaverModule.log("[YT] native player available: prog=${progressive.size} adap=${adaptive.size} adapUrls=$adaptiveUrls audio=${audio != null} mp4=${choices.size}")
    }

    private fun items(owner: Any, name: String) = (getField(owner, name) as? Iterable<*>)?.filterNotNull().orEmpty()
    private fun mime(owner: Any) = (getField(owner, "g") as? String).orEmpty()
    private fun validUrl(owner: Any) = (getField(owner, "f") as? String)?.takeIf { NativePlaybackPayload.validMediaUrl(it) }

    private fun findField(type: Class<*>, name: String): Field? =
        generateSequence(type) { it.superclass }
            .firstNotNullOfOrNull { cls -> runCatching { cls.getDeclaredField(name).apply { isAccessible = true } }.getOrNull() }

    private fun getField(owner: Any, name: String): Any? = findField(owner.javaClass, name)?.get(owner)

    private fun setField(owner: Any, name: String, value: Any?) {
        findField(owner.javaClass, name)?.set(owner, value)
    }

    private fun findMethod(type: Class<*>, name: String, argCount: Int): Method? =
        generateSequence(type) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                cls.declaredMethods.firstOrNull { it.name == name && it.parameterCount == argCount }?.apply { isAccessible = true }
            }

    private fun invoke(owner: Any, name: String, vararg args: Any?): Any? =
        findMethod(owner.javaClass, name, args.size)?.invoke(owner, *args)

    private const val MAX_AGE = 10 * 60_000L
}
