package org.tubepipe.core.service

import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

class ExtractorInitializerTest {
    @Test
    fun initializeKeepsTlsCertificateAndHostnameChecks() {
        val socketFactoryBefore = HttpsURLConnection.getDefaultSSLSocketFactory()
        val hostnameVerifierBefore = HttpsURLConnection.getDefaultHostnameVerifier()

        ExtractorInitializer.initialize(languageCode = "en", countryCode = "US")

        assertSame(socketFactoryBefore, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(hostnameVerifierBefore, HttpsURLConnection.getDefaultHostnameVerifier())
    }

    @Test
    fun initializeAndSettingsChangeSetTheContentCountry() {
        ExtractorInitializer.initialize(languageCode = "en", countryCode = "US")
        assertEquals("US", NewPipe.getPreferredContentCountry().countryCode)

        ExtractorInitializer.setContentCountry("BD")
        assertEquals("BD", NewPipe.getPreferredContentCountry().countryCode)
    }
}
