package org.tubepipe.core.service

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * List items carry YouTube's relative text ("2 days ago"), but a video page gives an ISO
 * timestamp. Show the timestamp as a medium local date and leave relative text alone.
 */
object UploadDateText {
    fun from(text: String?, locale: Locale = Locale.getDefault()): String? {
        if (text == null) {
            return null
        }
        return try {
            val uploadedAt = OffsetDateTime.parse(text)
            uploadedAt.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        } catch (notATimestamp: DateTimeParseException) {
            text
        }
    }
}
