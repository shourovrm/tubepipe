package org.tubepipe.core.player

import android.content.Context
import androidx.annotation.MainThread
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.tubepipe.core.player.webview.WebViewJavaScriptDecoder
import org.tubepipe.core.player.youtube.LocalDomPoTokenProvider

/** Process-wide playback setup that needs a Context; the extractor itself is started by ExtractorInitializer. */
object PlaybackRuntime {
    private var isInitialized = false

    /**
     * Safe to call before every playback: only the first call does work. The player screen calls
     * it instead of the Application, so the WebView setup never delays cold start to Home.
     */
    @MainThread
    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }
        val applicationContext = context.applicationContext
        YoutubeApiDecoder.setLocalDecoder(WebViewJavaScriptDecoder(applicationContext))
        LocalDomPoTokenProvider.initialize(applicationContext)
        NewPipe.setYoutubePoTokenResolver { videoId -> LocalDomPoTokenProvider.getPlayerPoToken(videoId) }
        isInitialized = true
    }
}
