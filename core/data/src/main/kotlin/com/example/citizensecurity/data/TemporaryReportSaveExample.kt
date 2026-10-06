package com.example.citizensecurity.data

import android.content.Context
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Demostración de un guardado real de SQLite con datos ficticios y una base en memoria.
 * No recibe datos del usuario ni abre la base local de reportes. Cada ejecución cierra y
 * descarta su base antes de devolver el reporte de ejemplo para mostrarlo en pantalla.
 */
class TemporaryReportSaveExample(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val applicationContext = context.applicationContext

    suspend fun run(): Report = withContext(ioDispatcher) {
        SqliteReportRepository(
            applicationContext,
            databaseName = null,
            clock = clock,
            ioDispatcher = ioDispatcher,
        ).use { repository ->
            repository.create(
                NewReport(
                    type = IncidentType.RISK,
                    priority = Priority.MEDIUM,
                    description = "Ejemplo ficticio: luminaria dañada frente al parque.",
                    occurredAt = clock.instant().minusSeconds(30),
                    location = ReportLocation(
                        reference = "Parque de ejemplo",
                        latitude = 19.4326077,
                        longitude = -99.1332088,
                    ),
                ),
            )
        }
    }
}
