package db.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HourlyLoadPublishedDateTest {
    private val formatter =
        DateTimeFormatter
            .ofPattern("d/M/yyyy")
            .withZone(ZoneId.of("America/Lima"))
            .withLocale(Locale.forLanguageTag("es-PE"))

    @Test
    fun `verify published dates format correctly in Peru timezone using production resolver`() {
        val testLoads =
            mapOf(
                "Carga Horaria 2025-2 Oficial" to "26/8/2025",
                "Carga Horaria 2026-1 Oficial" to "12/3/2026",
                "Carga Horaria 2026-2 Oficial" to "24/8/2026",
                "Carga Horaria 2026-2 Oficial V2" to "27/8/2026",
                "Carga Horaria 2026-2 Oficial V3" to "28/8/2026",
                "Carga Horaria 2026-2 Preliminar V2" to "23/8/2026",
            )

        testLoads.forEach { (loadName, expectedFormattedDate) ->
            val resolvedInstant = resolveHourlyLoadPublishedAt(loadName, fileLastModified = Instant.EPOCH)
            val formattedDate = formatter.format(resolvedInstant)
            assertEquals(expectedFormattedDate, formattedDate, "Mismatch for $loadName")
        }

        val official20262Instant = resolveHourlyLoadPublishedAt("Carga Horaria 2026-2 Oficial", fileLastModified = Instant.EPOCH)
        val expected20262Instant =
            LocalDate
                .of(2026, 8, 24)
                .atStartOfDay(ZoneId.of("America/Lima"))
                .toInstant()
        assertEquals(expected20262Instant, official20262Instant, "Instant mismatch for Carga Horaria 2026-2 Oficial")

        val officialV2Instant =
            resolveHourlyLoadPublishedAt("Carga Horaria 2026-2 Oficial V2", fileLastModified = Instant.EPOCH)
        val expectedOfficialV2Instant =
            LocalDate
                .of(2026, 8, 27)
                .atStartOfDay(ZoneId.of("America/Lima"))
                .toInstant()
        assertEquals(expectedOfficialV2Instant, officialV2Instant, "Instant mismatch for Carga Horaria 2026-2 Oficial V2")

        val officialV3Instant =
            resolveHourlyLoadPublishedAt("Carga Horaria 2026-2 Oficial V3", fileLastModified = Instant.EPOCH)
        val expectedOfficialV3Instant =
            LocalDate
                .of(2026, 8, 28)
                .atStartOfDay(ZoneId.of("America/Lima"))
                .toInstant()
        assertEquals(expectedOfficialV3Instant, officialV3Instant, "Instant mismatch for Carga Horaria 2026-2 Oficial V3")
    }
}
