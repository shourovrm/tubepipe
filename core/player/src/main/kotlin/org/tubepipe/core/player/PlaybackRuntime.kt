package org.tubepipe.core.player

import android.content.Context
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.tubepipe.core.player.webview.WebViewJavaScriptDecoder
import org.tubepipe.core.player.youtube.LocalDomPoTokenProvider

/** Process-wide playback setup that needs a Context; the extractor itself is started by ExtractorInitializer. */
object PlaybackRuntime {
    /** Call once on the main thread, because the WebView must be created there. */
    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        YoutubeApiDecoder.setLocalDecoder(WebViewJavaScriptDecoder(applicationContext))
        LocalDomPoTokenProvider.initialize(applicationContext)
        NewPipe.setYoutubePoTokenResolver { videoId -> LocalDomPoTokenProvider.getPlayerPoToken(videoId) }
    }
}
