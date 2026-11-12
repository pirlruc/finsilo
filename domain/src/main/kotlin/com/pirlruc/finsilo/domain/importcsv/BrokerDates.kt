package com.pirlruc.finsilo.domain.importcsv

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Calendar dates from T212 (ISO), DEGIRO (dd-MM-yyyy), and Revolut (ISO or dd/MM/yyyy). */
internal object BrokerDates {
    private val europeanDash: DateTimeFormatter = DateTimeFormatter.ofPattern("d-M-yyyy")
    private val europeanSlash: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")
    private val isoDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][ ]HH:mm[:ss][.SSS]")
    private val englishDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, uuuu, h:mm:ss a", Locale.ENGLISH)

    fun parse(raw: String): LocalDate? {
        val trimmed = clean(raw)
        if (trimmed.isEmpty()) return null
        return parseInstant(trimmed)
            ?: parseIsoDateTime(trimmed)
            ?: parseIsoDate(trimmed)
            ?: parseEuropean(trimmed)
            ?: parseEnglish(trimmed)
    }

    private fun clean(raw: String): String = raw.trim()
        .replace("\u00e2\u0080\u00af", " ")
        .replace("\u202f", " ")
        .replace("\u00a0", " ")
        .replace(Regex("\\s+"), " ")

    private fun parseEnglish(raw: String): LocalDate? = runCatching { LocalDateTime.parse(raw, englishDateTime).toLocalDate() }.getOrNull()

    private fun parseInstant(raw: String): LocalDate? = runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()

    private fun parseIsoDate(raw: String): LocalDate? {
        val datePart = raw.take(10)
        return runCatching { LocalDate.parse(datePart) }.getOrNull()
    }

    private fun parseIsoDateTime(raw: String): LocalDate? = try {
        LocalDate.parse(raw.replace('T', ' ').substringBefore('.').trim(), isoDateTime)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseEuropean(raw: String): LocalDate? {
        val datePart = raw.takeWhile { it != ' ' && it != 'T' }
        return runCatching { LocalDate.parse(datePart, europeanDash) }.getOrNull()
            ?: runCatching { LocalDate.parse(datePart, europeanSlash) }.getOrNull()
    }
}
