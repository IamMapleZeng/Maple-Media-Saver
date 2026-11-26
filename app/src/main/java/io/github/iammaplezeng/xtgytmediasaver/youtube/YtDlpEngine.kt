package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File

internal object YtDlpEngine {
    fun resolve(context: Context, link: String): VideoManifest {
        val canonical = YouTubeLink.parse(link) ?: throw DownloadFailure(DownloadError.INVALID_LINK)
        val output = YtDlpRuntime.run(context, listOf("--dump-single-json", "--skip-download", "--no-warnings", "--", canonical), 100, true)
        val json = output.lineSequence().firstOrNull { it.startsWith('{') } ?: throw DownloadFailure(DownloadError.PARSER)
        return YtDlpFormats.parse(json, canonical)
    }
    suspend fun download(context: Context, manifest: VideoManifest, choice: VideoChoice, directory: File, progress: (Int, Phase) -> Unit): File {
        val selection = choice.ytDlpFormat.takeIf(YtDlpFormats::validSelection) ?: throw DownloadFailure(DownloadError.NO_FORMAT)
        val output = File(directory, "media.%(ext)s")
        val tracks = selection.split('+').size
        var completed = 0
        var previous = 0
        var maximum = 0
        runInterruptible(Dispatchers.IO) {
            YtDlpRuntime.run(context, listOf("--no-simulate", "--newline", "--progress", "--progress-template", "download:XTG_PROGRESS:%(progress._percent_str)s",
                "--no-part", "--format", selection, "--merge-output-format", "mp4", "--remux-video", "mp4",
                "--match-filter", "availability = public & age_limit < 18 & !is_live",
                "--output", output.path, "--", manifest.link), 6 * 60 * 60, false, onProgress = { percent ->
                    if (percent < previous && previous >= 99) completed++
                    previous = percent
                    maximum = maxOf(maximum, ((completed * 100 + percent) * 90 / tracks).coerceAtMost(90))
                    progress(maximum, if (completed == 0) Phase.VIDEO else Phase.AUDIO)
                }, onLine = { line ->
                    if (line.startsWith("[Merger]") || line.startsWith("[VideoRemuxer]")) progress(92, Phase.MERGING)
                })
        }
        val result = File(directory, "media.mp4")
        if (!result.isFile || result.length() <= 0) throw DownloadFailure(DownloadError.NO_FORMAT)
        return result
    }
}
