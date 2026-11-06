package org.tubepipe.core.player

import android.content.Context
import androidx.annotation.MainThread
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.tubepipe.core.player.webview.WebViewJavaScriptDecoder
import org.tubepipe.core.player.youtube.LocalDomPoTokenProvider

/** Process-wide playback setup that needs a Context; the extractor itself is started by ExtractorInitializer. */
object PlaybackRuntime {
    private var decoder: WebViewJavaScriptDecoder? = null

    /**
     * Safe to call before every playback: only the first call does work. The Activity calls it
     * shortly after Home is up, and the player screen as a fallback, so the WebView setup never
     * delays cold start to Home.
     */
    @MainThread
    fun initialize(context: Context) {
        if (decoder != null) {
            return
        }
        val applicationContext = context.applicationContext
        val localDecoder = WebViewJavaScriptDecoder(applicationContext)
        YoutubeApiDecoder.setLocalDecoder(localDecoder)
        LocalDomPoTokenProvider.initialize(applicationContext)
        NewPipe.setYoutubePoTokenResolver { videoId -> LocalDomPoTokenProvider.getPlayerPoToken(videoId) }
        decoder = localDecoder
    }

    /**
     * Fetches the player script and compiles it in the WebView, which the first ciphered video
     * otherwise waits several seconds for on the box. Blocking; call off the main thread after [initialize].
     */
    fun prewarm() {
        decoder?.prewarm()
    }
}
