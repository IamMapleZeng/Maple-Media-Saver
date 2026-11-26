package io.github.iammaplezeng.xtgmediasaver.youtube

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.ByteArrayOutputStream
import java.io.IOException

internal object YouTubeResolver {
    private data class InnertubeProfile(
        val clientName: String,
        val clientVersion: String,
        val clientId: String,
        val userAgent: String,
        val extraClientFields: Map<String, Any>,
        val embedThirdParty: Boolean = false
    )

    private val profiles = listOf(
        InnertubeProfile(
            clientName = "IOS_PRODUCER",
            clientVersion = "0.147.945",
            clientId = "92",
            userAgent = YouTubeNetwork.IOS_PRODUCER_USER_AGENT,
            extraClientFields = mapOf(
                "deviceMake" to "Apple", "deviceModel" to "iPhone16,2",
                "osName" to "iPhone", "osVersion" to "18.3.2.22D82"
            )
        ),
        InnertubeProfile(
            clientName = "VISIONOS", clientVersion = "1.02", clientId = "101",
            userAgent = YouTubeNetwork.VISIONOS_USER_AGENT,
            extraClientFields = mapOf(
                "deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS", "osVersion" to "26.5.23O471"
            )
        ),
        InnertubeProfile(
            clientName = "IOS",
            clientVersion = "21.26.4",
            clientId = "5",
            userAgent = YouTubeNetwork.IOS_USER_AGENT,
            extraClientFields = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.3.2.22D82"
            )
        )
    )

    private val initialized by lazy { NewPipe.init(ExtractorDownloader()); true }

    fun resolve(link: String, permissionConfirmed: Boolean, visitorData: String? = null): VideoManifest {
        if (!permissionConfirmed) throw DownloadFailure(DownloadError.PERMISSION)
        val canonical = YouTubeLink.parse(link) ?: throw DownloadFailure(DownloadError.INVALID_LINK)
        val videoId = canonical.substringAfter("v=")

        var botError: SignInConfirmNotBotException? = null
        var lastError: Throwable? = null
        for (profile in profiles) {
            try {
                return resolveViaInnertube(canonical, videoId, permissionConfirmed, profile, visitorData)
            } catch (failure: DownloadFailure) {
                if (failure.reason == DownloadError.PERMISSION) {
                    throw failure
                }
                lastError = failure
            } catch (bot: SignInConfirmNotBotException) {
                if (botError == null) botError = bot
            } catch (error: Throwable) {
                lastError = error
            }
        }

        return try {
            resolveViaNewPipe(canonical, permissionConfirmed)
        } catch (fallbackError: Throwable) {
            if (fallbackError is SignInConfirmNotBotException || botError != null) {
                throw (fallbackError as? SignInConfirmNotBotException) ?: checkNotNull(botError)
            }
            throw if (fallbackError is DownloadFailure) fallbackError else (lastError ?: fallbackError)
        }
    }

    private fun resolveViaInnertube(
        canonical: String,
        videoId: String,
        permissionConfirmed: Boolean,
        profile: InnertubeProfile,
        visitorData: String?
    ): VideoManifest {
        val clientObj = JSONObject().apply {
            put("clientName", profile.clientName)
            put("clientVersion", profile.clientVersion)
            put("hl", "en")
            put("gl", "US")
            profile.extraClientFields.forEach { (k, v) -> put(k, v) }
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData.take(512))
            }
        }
        val contextObj = JSONObject().put("client", clientObj)
        if (profile.embedThirdParty) {
            contextObj.put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/"))
        }
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", contextObj)
        }.toString().toByteArray(Charsets.UTF_8)

        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "User-Agent" to listOf(profile.userAgent),
            "X-YouTube-Client-Name" to listOf(profile.clientId),
            "X-YouTube-Client-Version" to listOf(profile.clientVersion)
        )
        val connection = YouTubeNetwork.open(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            media = false,
            method = "POST",
            headers = headers,
            body = payload
        )
        val responseText = try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Innertube HTTP $code")
            connection.inputStream.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    if (out.size() + n > 12 * 1024 * 1024) throw IOException("Innertube response too large")
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }

        val root = JSONObject(responseText)
        val playability = root.optJSONObject("playabilityStatus") ?: JSONObject()
        val status = playability.optString("status", "")
        val reason = playability.optString("reason", "")
        if (status != "OK") {
            if (isBotVerificationFailure(status, reason)) {
                throw SignInConfirmNotBotException(reason.ifBlank { status })
            }
            throw DownloadFailure(DownloadError.RESTRICTED)
        }

        val details = root.optJSONObject("videoDetails") ?: JSONObject()
        if (details.optString("videoId") != videoId) throw DownloadFailure(DownloadError.RESTRICTED)
        val streaming = root.optJSONObject("streamingData") ?: throw DownloadFailure(DownloadError.NO_FORMAT)

        // Unlisted links shared directly by URL are downloadable when public/unlisted; only strictly private videos are blocked.
        val publicVideo = !details.optBoolean("isPrivate", false)
        val isLive = details.optBoolean("isLive", false) ||
            details.optBoolean("isLiveContent", false) ||
            details.optBoolean("isUpcoming", false) ||
            streaming.optString("hlsManifestUrl", "").isNotEmpty()
        // Note: paygatedQualitiesMetadata is present on almost all free 1080p videos (for "1080p Premium" bitrate upsell), so never treat it as paid!
        val isPaid = details.optBoolean("isPaid", false)
        val ageLimit = if (playability.optBoolean("adult", false)) 18 else 0

        val progressiveList = streaming.optJSONArray("formats").toObjects()
        val adaptiveList = streaming.optJSONArray("adaptiveFormats").toObjects()
        val hasDrm = (progressiveList + adaptiveList).any { (it.optJSONArray("drmFamilies")?.length() ?: 0) > 0 }

        val facts = AccessFacts(
            publicVideo = publicVideo,
            ageLimit = ageLimit,
            live = isLive,
            paid = isPaid,
            drm = hasDrm
        )
        if (!DownloadPolicy.allowed(facts, permissionConfirmed)) {
            throw DownloadFailure(DownloadError.RESTRICTED)
        }

        val bestAudio = adaptiveList
            .filter {
                val mime = it.optString("mimeType", "")
                val url = it.optString("url", "")
                mime.startsWith("audio/mp4") && mime.contains("mp4a", ignoreCase = true) &&
                    NativePlaybackPayload.validMediaUrl(url)
            }
            .maxByOrNull { it.optInt("averageBitrate", it.optInt("bitrate", 0)) }
            ?.optString("url")

        val taggedFormats = ArrayList<Pair<JSONObject, Boolean>>(progressiveList.size + adaptiveList.size).apply {
            progressiveList.forEach { add(it to false) }
            adaptiveList.forEach { add(it to true) }
        }

        val choices = taggedFormats.mapNotNull { pair ->
            val fmt = pair.first
            val separate = pair.second
            val mime = fmt.optString("mimeType", "")
            val height = fmt.optInt("height", 0)
            val url = fmt.optString("url", "")
            if (!mime.startsWith("video/mp4") || !mime.contains("avc", ignoreCase = true)) return@mapNotNull null
            if (height !in 1..4320 || !NativePlaybackPayload.validMediaUrl(url)) return@mapNotNull null
            if (separate && bestAudio == null) return@mapNotNull null
            VideoChoice("${height}p · MP4", height, url, if (separate) bestAudio else null)
        }.sortedWith(compareByDescending<VideoChoice> { it.height }.thenBy { it.audioUrl != null })
            .distinctBy { it.height }

        if (choices.isEmpty()) throw DownloadFailure(DownloadError.NO_FORMAT)
        val title = details.optString("title", "").take(500).ifBlank { "YouTube" }
        return VideoManifest(canonical, title, choices)
    }

    internal fun isBotVerificationFailure(status: String, reason: String): Boolean {
        val lowerReason = reason.lowercase()
        if ("bot" in lowerReason || "機器人" in lowerReason || "机器人" in lowerReason) return true
        return status.equals("LOGIN_REQUIRED", ignoreCase = true) &&
            !lowerReason.contains("inappropriate") &&
            !lowerReason.contains("age") &&
            !lowerReason.contains("private") &&
            !lowerReason.contains("members")
    }

    private fun JSONArray?.toObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        val list = ArrayList<JSONObject>(length())
        for (i in 0 until length()) {
            optJSONObject(i)?.let { list.add(it) }
        }
        return list
    }

    private fun resolveViaNewPipe(canonical: String, permissionConfirmed: Boolean): VideoManifest {
        check(initialized)
        val extractor = ServiceList.YouTube.getStreamExtractor(canonical)
        extractor.fetchPage()
        val facts = AccessFacts(
            extractor.privacy == StreamExtractor.Privacy.PUBLIC,
            extractor.ageLimit,
            extractor.streamType != StreamType.VIDEO_STREAM,
            extractor.contentAvailability in setOf(ContentAvailability.MEMBERSHIP, ContentAvailability.PAID, ContentAvailability.UPCOMING),
            false
        )
        if (!DownloadPolicy.allowed(facts, permissionConfirmed)) throw DownloadFailure(DownloadError.RESTRICTED)
        val info = StreamInfo.getInfo(extractor)
        val audio = info.audioStreams.filter {
            it.format == MediaFormat.M4A && it.isUrl &&
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && YouTubeNetwork.mediaAllowed(it.content)
        }.maxByOrNull { it.averageBitrate }?.content

        val choices = (info.videoStreams + info.videoOnlyStreams).filter {
            it.format == MediaFormat.MPEG_4 && it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                YouTubeNetwork.mediaAllowed(it.content) && (it.codec?.startsWith("avc", true) != false) && (!it.isVideoOnly() || audio != null)
        }.map { stream ->
            val height = Regex("\\d+").find(stream.getResolution())?.value?.toIntOrNull() ?: 0
            VideoChoice("${stream.getResolution()} · MP4", height, stream.content, if (stream.isVideoOnly()) audio else null)
        }.filter { it.height > 0 }
            .sortedWith(compareByDescending<VideoChoice> { it.height }.thenBy { it.audioUrl != null })
            .distinctBy { it.height }
        if (choices.isEmpty()) throw DownloadFailure(DownloadError.NO_FORMAT)
        return VideoManifest(canonical, info.name, choices)
    }
}
