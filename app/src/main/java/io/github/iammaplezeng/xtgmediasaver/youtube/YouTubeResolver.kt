package io.github.iammaplezeng.xtgmediasaver.youtube

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

internal object YouTubeResolver {
    private val initialized by lazy { NewPipe.init(ExtractorDownloader()); true }
    fun resolve(link: String, permissionConfirmed: Boolean): VideoManifest {
        if (!permissionConfirmed) throw DownloadFailure(DownloadError.PERMISSION)
        val canonical = YouTubeLink.parse(link) ?: throw DownloadFailure(DownloadError.INVALID_LINK)
        check(initialized)
        val extractor = ServiceList.YouTube.getStreamExtractor(canonical)
        extractor.fetchPage()
        // Check eligibility before requesting stream extraction. No login/cookie/age-gate fallback.
        val facts = AccessFacts(extractor.privacy == StreamExtractor.Privacy.PUBLIC, extractor.ageLimit,
            extractor.streamType != StreamType.VIDEO_STREAM,
            extractor.contentAvailability in setOf(ContentAvailability.MEMBERSHIP, ContentAvailability.PAID, ContentAvailability.UPCOMING), false)
        if (!DownloadPolicy.allowed(facts, permissionConfirmed)) throw DownloadFailure(DownloadError.RESTRICTED)
        val info = StreamInfo.getInfo(extractor)
        val audio = info.audioStreams.filter { it.format == MediaFormat.M4A && it.isUrl &&
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && YouTubeNetwork.mediaAllowed(it.content) }
            .maxByOrNull { it.averageBitrate }?.content
        val choices = (info.videoStreams + info.videoOnlyStreams).filter {
            it.format == MediaFormat.MPEG_4 && it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                YouTubeNetwork.mediaAllowed(it.content) && (it.codec?.startsWith("avc", true) != false) && (!it.isVideoOnly() || audio != null)
        }.map { stream ->
            val height = Regex("\\d+").find(stream.getResolution())?.value?.toIntOrNull() ?: 0
            VideoChoice("${stream.getResolution()} · MP4", height, stream.content, if (stream.isVideoOnly()) audio else null)
        }.filter { it.height > 0 }.sortedWith(compareByDescending<VideoChoice> { it.height }.thenBy { it.audioUrl != null })
            .distinctBy { it.height }
        if (choices.isEmpty()) throw DownloadFailure(DownloadError.NO_FORMAT)
        return VideoManifest(canonical, info.name, choices)
    }
}
