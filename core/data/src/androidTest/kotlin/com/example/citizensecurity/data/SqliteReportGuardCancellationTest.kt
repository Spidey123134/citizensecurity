package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteReportGuardCancellationTest {
    @Test fun cancelarEnElPrimerGuardNoAbreLaBase() = cancelledInsideGuard(1)
    @Test fun cancelarAntesDeIniciarTransaccionNoInserta() = cancelledInsideGuard(2)
    @Test fun cancelarEnElGuardFinalRevierteLaInsercion() = cancelledInsideGuard(3)

    private fun cancelledInsideGuard(cancelAt: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "citizensecurity_guard_cancel_test_${UUID.randomUUID()}.db"
        val now = Instant.parse("2026-10-03T03:00:00Z")
        var calls = 0
        var caller: kotlinx.coroutines.Job? = null
        val repository = SqliteReportRepository(
            context, name, Clock.fixed(now, ZoneOffset.UTC), Dispatchers.Unconfined,
            createGuard = { if (++calls == cancelAt) requireNotNull(caller).cancel() },
        )
        try {
            val pending = async {
                caller = currentCoroutineContext().job
                repository.create(NewReport(
                    IncidentType.RISK, Priority.LOW, "Hay una luminaria dañada junto al parque.",
                    now.minusSeconds(10), ReportLocation("Frente al parque central"),
                ))
            }
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals(cancelAt, calls)
            if (cancelAt == 1) assertFalse(context.getDatabasePath(name).exists())
            assertEquals(0, repository.list().size)
        } finally {
            repository.close()
            check(name.matches(Regex("citizensecurity_guard_cancel_test_[0-9a-f-]{36}\\.db")))
            context.deleteDatabase(name)
        }
    }
}
