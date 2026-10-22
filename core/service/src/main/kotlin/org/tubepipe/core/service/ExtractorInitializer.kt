package org.tubepipe.core.service

import javax.net.ssl.HttpsURLConnection
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.tubepipe.core.service.network.DownloaderImpl

/** Starts the extractor once per process, before any repository or the player runs. */
object ExtractorInitializer {
    fun initialize(languageCode: String, countryCode: String) {
        val socketFactoryBefore = HttpsURLConnection.getDefaultSSLSocketFactory()
        val hostnameVerifierBefore = HttpsURLConnection.getDefaultHostnameVerifier()

        NewPipe.init(
            DownloaderImpl.init(null),
            Localization(languageCode, countryCode),
            ContentCountry(countryCode),
        )

        // NewPipe.init calls trustEveryone(), which switches off certificate and hostname checks
        // for every HttpsURLConnection in the process. Put the platform defaults back.
        HttpsURLConnection.setDefaultSSLSocketFactory(socketFactoryBefore)
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifierBefore)
    }

    /** Applies the content country chosen in Settings to every later extractor call. */
    fun setContentCountry(countryCode: String) {
        NewPipe.setPreferredContentCountry(ContentCountry(countryCode))
    }

    /** YouTube Restricted Mode (the parental "hide sensitive content" setting) for every later request. */
    fun setRestrictedMode(enabled: Boolean) {
        DownloaderImpl.setRestrictedMode(enabled)
    }
}
