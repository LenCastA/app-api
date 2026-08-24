package db.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
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
                "Carga Horaria 2026-2 Preliminar V2" to "23/8/2026",
            )

        testLoads.forEach { (loadName, expectedFormattedDate) ->
            val resolvedInstant = resolveHourlyLoadPublishedAt(loadName, fileLastModified = Instant.EPOCH)
            val formattedDate = formatter.format(resolvedInstant)
            assertEquals(expectedFormattedDate, formattedDate, "Mismatch for $loadName")
        }
    }
}
