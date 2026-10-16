package com.example.citizensecurity.account

import com.example.citizensecurity.data.ReportAdministrationSources
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportAdministrationBindingTest {
    @Test fun capturingARealAccountDoesNotCallAuthorityOrReadStorage() {
        val fixture = Fixture()
        val binding = fixture.bind()
        assertEquals(ReportStatusChangeScope(fixture.account.id, fixture.account.id), binding.scope)
        assertSame(binding.scope, binding.reader.scope)
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun defaultPolicyDeniesAllReadersWithoutTouchingTheirDelegates() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind(DenyAllReportAdministrationPolicy)
        Read.entries.forEach { kind -> denied { fixture.read(binding, kind) } }
        assertEquals(0, fixture.reads)
    }

    @Test fun academicWorkspaceIsDeniedEvenByAnOtherwisePermissiveAuthority() {
        val fixture = Fixture(signedIn = false)
        deniedNow { fixture.bind(ReportAdministrationPolicy { _, _ -> error("Academic identity reached authority") }) }
        assertEquals(0, fixture.reads)
    }

    @Test fun sourceOwnerActorAndAcademicMismatchesAreRejectedAtCapture() {
        val fixture = Fixture()
        val correct = fixture.sources.scope
        listOf(correct.copy(ownerAccountId = id(9)), correct.copy(actorAccountId = id(9)),
            correct.copy(ownerAccountId = null)).forEach { wrong ->
            deniedNow { fixture.bind(sources = fixture.sources(wrong)) }
        }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun obsoleteSessionCannotBeBoundAfterLoggingOutAndBackIntoTheSameAccount() {
        val fixture = Fixture()
        fixture.sessions.signOut()
        fixture.sessions.signIn(fixture.account)
        revokedNow { fixture.bind() }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun grantedReadersUseOnlyTheCapturedSourcesAndExactQueries() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        assertSame(fixture.report, binding.reader.findById(fixture.report.id))
        assertSame(fixture.page, binding.reader.listPage(fixture.pageQuery))
        assertSame(fixture.history, binding.reader.readHistory(fixture.historyQuery))
        assertEquals(listOf(fixture.report.id, fixture.pageQuery, fixture.historyQuery), fixture.queries)
        assertEquals(3, fixture.reads)
        assertTrue(fixture.checkedScopes.all { it == binding.scope })
        assertTrue(fixture.checkedPermissions.all { it == ReportAdministrationPermission.READ_REPORTS })
    }

    @Test fun changingToAnotherOwnerRevokesEveryOldReaderBeforeAuthorityAndIo() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        fixture.sessions.signIn(LocalAccount(id(2), "alumna2"))
        Read.entries.forEach { kind -> revoked { fixture.read(binding, kind) } }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun logoutAndLoginAsTheSameOwnerCannotReviveAnExistingBinding() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        fixture.sessions.signOut()
        fixture.sessions.signIn(fixture.account)
        Read.entries.forEach { kind -> revoked { fixture.read(binding, kind) } }
        revokedNow { binding.policy.check(binding.scope, ReportAdministrationPermission.CHANGE_STATUS) }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun signingInAgainWithoutLogoutStillRevokesTheOldRevision() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        fixture.sessions.signIn(fixture.account.copy())
        revoked { binding.reader.findById(fixture.report.id) }
        assertTrue(fixture.sessions.capture().revision > fixture.session.revision)
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun closingTheCapturedWorkspaceRevokesReadersAndReusablePolicy() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        fixture.open = false
        Read.entries.forEach { kind -> revoked { fixture.read(binding, kind) } }
        revokedNow { binding.policy.check(binding.scope, ReportAdministrationPermission.CHANGE_STATUS) }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun boundPolicyRejectsAnotherOwnerActorAndAcademicScopeBeforeAuthority() {
        val fixture = Fixture()
        val binding = fixture.bind()
        listOf(binding.scope.copy(ownerAccountId = id(2)), binding.scope.copy(actorAccountId = id(2)),
            binding.scope.copy(ownerAccountId = null)).forEach { wrong ->
            ReportAdministrationPermission.entries.forEach { permission ->
                deniedNow { binding.policy.check(wrong, permission) }
            }
        }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun permissionToReadDoesNotPermitAStatusChange() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        assertSame(fixture.report, binding.reader.findById(fixture.report.id))
        deniedNow { binding.policy.check(binding.scope, ReportAdministrationPermission.CHANGE_STATUS) }
        assertEquals(1, fixture.reads)
    }

    @Test fun permissionToChangeDoesNotPermitReadingTheWorkspace() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind(ReportAdministrationPolicy { scope, permission ->
            assertEquals(fixture.sources.scope, scope)
            if (permission != ReportAdministrationPermission.CHANGE_STATUS) throw ReportAdministrationPermissionDeniedException()
        })
        binding.policy.check(binding.scope, ReportAdministrationPermission.CHANGE_STATUS)
        Read.entries.forEach { kind -> denied { fixture.read(binding, kind) } }
        assertEquals(0, fixture.reads)
    }

    @Test fun revocationOfAuthorityIsRecheckedOnTheNextPage() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        binding.reader.listPage(fixture.pageQuery)
        fixture.permitted = false
        denied { binding.reader.listPage(ReportListPageQuery(before = ReportListCursor.from(fixture.report))) }
        assertEquals(1, fixture.reads)
    }

    @Test fun logoutWhileAnAuthorityLookupBlocksPreventsStorageEntry() = runTest {
        val fixture = Fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val binding = fixture.bind(ReportAdministrationPolicy { _, _ ->
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Timed out waiting for authority fixture" }
        })
        val pending = async(Dispatchers.Default) { revoked { binding.reader.findById(fixture.report.id) } }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            fixture.sessions.signOut()
        } finally { release.countDown() }
        pending.await()
        assertEquals(0, fixture.reads)
    }

    @Test fun reusableChangePolicyRechecksTheSessionAfterABlockingAuthorityLookup() = runTest {
        val fixture = Fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val binding = fixture.bind(ReportAdministrationPolicy { _, permission ->
            assertEquals(ReportAdministrationPermission.CHANGE_STATUS, permission)
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Timed out waiting for authority fixture" }
        })
        val pending = async(Dispatchers.Default) {
            revokedNow { binding.policy.check(binding.scope, ReportAdministrationPermission.CHANGE_STATUS) }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            fixture.sessions.signOut()
            fixture.sessions.signIn(fixture.account)
        } finally { release.countDown() }
        pending.await()
        assertEquals(0, fixture.reads)
    }

    @Test fun authorityRevokedDuringEachSuspendedReadDiscardsTheResult() = runTest {
        Read.entries.forEach { kind ->
            val fixture = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.onRead = { entered.complete(Unit); release.await() }
            val binding = fixture.bind()
            val pending = async { denied { fixture.read(binding, kind) } }
            entered.await()
            fixture.permitted = false
            release.complete(Unit)
            pending.await()
            assertEquals(1, fixture.reads)
        }
    }

    @Test fun sessionReplacedDuringEachSuspendedReadDiscardsTheResultWithoutRetargeting() = runTest {
        Read.entries.forEach { kind ->
            val fixture = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.onRead = { entered.complete(Unit); release.await() }
            val binding = fixture.bind()
            val pending = async { revoked { fixture.read(binding, kind) } }
            entered.await()
            fixture.sessions.signIn(LocalAccount(id(2), "alumna2"))
            release.complete(Unit)
            pending.await()
            assertEquals(1, fixture.reads)
        }
    }

    @Test fun anAuthorityFailureDoesNotBecomeAnEmptyReportOrReadAnySource() = runTest {
        val fixture = Fixture()
        val failure = IOException("No se pudo comprobar el permiso")
        val binding = fixture.bind(ReportAdministrationPolicy { _, _ -> throw failure })
        try { binding.reader.findById(fixture.report.id); fail("Authority failure was hidden") }
        catch (actual: IOException) { assertSame(failure, actual) }
        assertEquals(0, fixture.reads)
    }

    @Test fun storageFailureIsNotRetriedOrConvertedIntoAFalseMissingReport() = runTest {
        val fixture = Fixture()
        val failure = IOException("No se pudo leer el reporte")
        fixture.onRead = { throw failure }
        val binding = fixture.bind()
        try { binding.reader.findById(fixture.report.id); fail("Storage failure was hidden") }
        catch (actual: IOException) { assertSame(failure, actual) }
        assertEquals(1, fixture.reads)
    }

    @Test fun anAlreadyCancelledReaderNeverCallsAuthorityOrStorage() = runTest {
        val fixture = Fixture()
        val binding = fixture.bind()
        val pending = async {
            currentCoroutineContext().cancel()
            binding.reader.findById(fixture.report.id)
        }
        cancelled { pending.await() }
        assertEquals(0, fixture.authorityChecks)
        assertEquals(0, fixture.reads)
    }

    @Test fun cancellationDiscardsDataFromANonCooperativeCapturedSource() = runTest {
        val fixture = Fixture()
        lateinit var completion: Continuation<Unit>
        fixture.onRead = { suspendCoroutine<Unit> { completion = it } }
        val binding = fixture.bind()
        var delivered = false
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            binding.reader.findById(fixture.report.id)
            delivered = true
        }
        pending.cancel()
        completion.resume(Unit)
        runCurrent()
        cancelled { pending.await() }
        assertFalse(delivered)
        assertEquals(1, fixture.reads)
    }

    private enum class Read { REPORT, PAGE, HISTORY }

    private class RevokedSession : IllegalStateException()

    private class Fixture(signedIn: Boolean = true) {
        val account = LocalAccount(id(1), "alumna1")
        val sessions = LocalAccountSession().apply { if (signedIn) signIn(this@Fixture.account) }
        val session = sessions.capture()
        var open = true
        var permitted = true
        var authorityChecks = 0
        var reads = 0
        var onRead: suspend () -> Unit = {}
        val queries = mutableListOf<Any>()
        val checkedScopes = mutableListOf<ReportStatusChangeScope>()
        val checkedPermissions = mutableListOf<ReportAdministrationPermission>()
        val at = Instant.parse("2026-10-15T15:00:00.123456789Z")
        val report = Report(id(3), IncidentType.RISK, Priority.MEDIUM, "Luminaria dañada frente al parque",
            at, ReportLocation("Parque"), at)
        val page = ReportListPage(listOf(report), false)
        val history = ReportHistoryPage(report.id, ReportStatus.REPORTED, 0L, emptyList(), false, false)
        val pageQuery = ReportListPageQuery(type = IncidentType.RISK, priority = Priority.MEDIUM, limit = 7)
        val historyQuery = ReportHistoryQuery(report.id, limit = 4)
        val sources = sources(ReportStatusChangeScope(account.id, account.id))
        val authority = ReportAdministrationPolicy { scope, permission ->
            authorityChecks++
            checkedScopes += scope
            checkedPermissions += permission
            if (!permitted || permission != ReportAdministrationPermission.READ_REPORTS)
                throw ReportAdministrationPermissionDeniedException()
        }

        fun bind(policy: ReportAdministrationPolicy = authority, sources: ReportAdministrationSources = this.sources) =
            ReportAdministrationBinding(session, {
                if (!open || !sessions.isCurrent(session)) throw RevokedSession()
            }, policy, sources)

        fun sources(scope: ReportStatusChangeScope) = ReportAdministrationSources(scope,
            { folio -> readSource(folio); report },
            ReportListPageRepository { query -> readSource(query); page },
            ReportHistoryRepository { query -> readSource(query); history })

        private suspend fun readSource(query: Any) { reads++; queries += query; onRead() }

        suspend fun read(binding: ReportAdministrationBinding, kind: Read): Any? = when (kind) {
            Read.REPORT -> binding.reader.findById(report.id)
            Read.PAGE -> binding.reader.listPage(pageQuery)
            Read.HISTORY -> binding.reader.readHistory(historyQuery)
        }
    }

    private companion object {
        fun id(number: Int) = "a0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
        inline fun deniedNow(block: () -> Any?) {
            try { block(); fail("Administrative access was granted") }
            catch (_: ReportAdministrationPermissionDeniedException) { }
        }
        suspend fun denied(block: suspend () -> Any?) {
            try { block(); fail("Administrative access was granted") }
            catch (_: ReportAdministrationPermissionDeniedException) { }
        }
        inline fun revokedNow(block: () -> Any?) {
            try { block(); fail("A revoked captured session was accepted") }
            catch (_: RevokedSession) { }
        }
        suspend fun revoked(block: suspend () -> Any?) {
            try { block(); fail("A revoked captured session returned data") }
            catch (_: RevokedSession) { }
        }
        suspend fun cancelled(block: suspend () -> Any?) {
            try { block(); fail("Cancellation was ignored") }
            catch (_: CancellationException) { }
        }
    }
}
