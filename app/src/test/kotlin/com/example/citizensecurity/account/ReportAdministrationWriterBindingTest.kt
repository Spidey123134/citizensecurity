package com.example.citizensecurity.account

import com.example.citizensecurity.data.ReportAdministrationSources
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportAdministrationWriterBindingTest {
    @Test fun capturingWriterDoesNotReadAuthorityCreateBackendOrEvaluateForeground() {
        val f = Fixture()
        val access = f.bind().openStatusChangeAccess { error("Foreground queried during construction") }
        assertEquals(f.scope, access.scope)
        assertEquals(0, f.authorityChecks)
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun defaultAndReadOnlyPoliciesCannotCreateWriterOrReadAReceipt() = runTest {
        listOf(DenyAllReportAdministrationPolicy, ReportAdministrationPolicy { _, permission ->
            if (permission != ReportAdministrationPermission.READ_REPORTS) throw ReportAdministrationPermissionDeniedException()
        }).forEach { policy ->
            val f = Fixture()
            val access = f.bind(policy).openStatusChangeAccess()
            denied { access.change(f.request) }
            denied { access.findReceipt(f.request) }
            denied { access.checkCurrent() }
            assertEquals(0, f.factoryCalls)
            assertEquals(0, f.entries)
        }
    }

    @Test fun missingCapturedWriterFactoryFailsClosedEvenWhenAuthorityAllows() = runTest {
        val f = Fixture()
        val access = f.bind(factory = null).openStatusChangeAccess()
        denied { access.change(f.request) }
        denied { access.findReceipt(f.request) }
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun anotherOwnerActorOrAcademicRequestNeverCallsAuthorityOrBackend() = runTest {
        val f = Fixture()
        val access = f.bind().openStatusChangeAccess()
        listOf(f.scope.copy(ownerAccountId = id(8)), f.scope.copy(actorAccountId = id(8)),
            f.scope.copy(ownerAccountId = null)).forEach { scope ->
            denied { access.change(f.request.copy(scope = scope)) }
            denied { access.findReceipt(f.request.copy(scope = scope)) }
        }
        assertEquals(0, f.authorityChecks)
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun capturedWriterUsesOneFactoryExactRequestAndCapturedPermission() = runTest {
        val f = Fixture()
        val binding = f.bind()
        val access = binding.openStatusChangeAccess()
        assertEquals(f.event, access.change(f.request))
        assertEquals(ReportStatusChangeReceipt(f.request.command.reportId, f.event), access.findReceipt(f.request))
        binding.checkStatusChangeAccess(access)
        assertEquals(1, f.factoryCalls)
        assertEquals(2, f.entries)
        assertTrue(f.requests.all { it === f.request })
        assertTrue(f.permissions.isNotEmpty())
        assertTrue(f.permissions.all { it == ReportAdministrationPermission.CHANGE_STATUS })
        assertTrue(f.scopes.all { it == f.scope })
    }

    @Test fun foregroundGuardRunsBeforeTheBackendFactory() = runTest {
        val f = Fixture()
        val access = f.bind().openStatusChangeAccess { throw WriteWindowClosed() }
        try { access.change(f.request); fail("A background gesture entered storage") }
        catch (_: WriteWindowClosed) { }
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun foregroundPolicyIsPassedInsideTheBackendBeforeCommit() = runTest {
        val f = Fixture()
        var foreground = true
        f.onEntry = { foreground = false }
        val access = f.bind().openStatusChangeAccess { if (!foreground) throw WriteWindowClosed() }
        try { access.change(f.request); fail("The before-commit policy lost its foreground guard") }
        catch (_: WriteWindowClosed) { }
        assertEquals(1, f.entries)
        assertEquals(0, f.commits)
    }

    @Test fun revokeAfterCommitRejectsCallbackAndKeepsExactlyOneBackendAttempt() = runTest {
        val f = Fixture()
        f.afterCommit = { f.allowed = false }
        denied { f.bind().openStatusChangeAccess().change(f.request) }
        assertEquals(1, f.entries)
        assertEquals(1, f.commits)
    }

    @Test fun logoutAndReloginSameOwnerCannotReviveCapturedWriter() = runTest {
        val f = Fixture()
        val access = f.bind().openStatusChangeAccess()
        f.sessions.signOut()
        f.sessions.signIn(f.account)
        denied { access.change(f.request) }
        denied { access.findReceipt(f.request) }
        assertEquals(0, f.authorityChecks)
        assertEquals(0, f.factoryCalls)
    }

    @Test fun sessionReplacedDuringAuthorityLookupNeverCreatesBackend() = runTest {
        val f = Fixture()
        val access = f.bind(ReportAdministrationPolicy { _, _ -> f.sessions.signIn(f.account) }).openStatusChangeAccess()
        denied { access.findReceipt(f.request) }
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun sessionReplacedDuringFactoryCannotEnterItsReturnedBackend() = runTest {
        val f = Fixture()
        f.onFactory = { f.sessions.signIn(LocalAccount(id(9), "otra_cuenta")) }
        val access = f.bind().openStatusChangeAccess()
        denied { access.change(f.request) }
        assertEquals(1, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun backendForAnotherScopeCannotBeUsed() = runTest {
        val f = Fixture()
        f.returnedScope = f.scope.copy(actorAccountId = id(5))
        denied { f.bind().openStatusChangeAccess().findReceipt(f.request) }
        assertEquals(1, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun journalGuardRejectsAnotherBindingEvenForIdenticalSessionAndUuids() = runTest {
        val f = Fixture()
        val first = f.bind()
        val second = f.bind()
        val access = first.openStatusChangeAccess()
        denied { second.checkStatusChangeAccess(access) }
        assertEquals(0, f.authorityChecks)
        assertEquals(0, f.factoryCalls)
        first.checkStatusChangeAccess(access)
        assertEquals(1, f.factoryCalls)
    }

    @Test fun unreadableAuthorityIsTypedDenialWithoutAnEmptyReceiptOrFactory() = runTest {
        val f = Fixture()
        val failure = IOException("Authority fixture unavailable")
        val access = f.bind(ReportAdministrationPolicy { _, _ -> throw failure }).openStatusChangeAccess()
        denied { access.findReceipt(f.request) }
        denied { access.change(f.request) }
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun authorityCancellationIsPreservedWithoutFactoryOrBackend() = runTest {
        val f = Fixture()
        val cancelled = CancellationException("Authority fixture cancelled")
        val access = f.bind(ReportAdministrationPolicy { _, _ -> throw cancelled }).openStatusChangeAccess()
        try { access.findReceipt(f.request); fail("Authority cancellation became a result") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
        assertEquals(0, f.factoryCalls)
        assertEquals(0, f.entries)
    }

    @Test fun alreadyCancelledOperationDoesNotEnterAuthorityOrFactory() = runTest {
        val f = Fixture()
        val access = f.bind().openStatusChangeAccess()
        val job = async { currentCoroutineContext().cancel(); access.findReceipt(f.request) }
        cancelled { job.await() }
        assertEquals(0, f.authorityChecks)
        assertEquals(0, f.factoryCalls)
    }

    @Test fun cancellationDiscardsANonCooperativeReceiptWithoutAnotherAttempt() = runTest {
        val f = Fixture()
        lateinit var completion: Continuation<Unit>
        f.onEntry = { suspendCoroutine { completion = it } }
        val access = f.bind().openStatusChangeAccess()
        var delivered = false
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            access.findReceipt(f.request)
            delivered = true
        }
        job.cancel()
        completion.resume(Unit)
        runCurrent()
        cancelled { job.await() }
        assertFalse(delivered)
        assertEquals(1, f.factoryCalls)
        assertEquals(1, f.entries)
    }

    private class WriteWindowClosed : IllegalStateException()
    private class RevokedSession : IllegalStateException()

    private class Fixture {
        val account = LocalAccount(id(1), "admin_local")
        val sessions = LocalAccountSession().apply { signIn(this@Fixture.account) }
        val session = sessions.capture()
        val scope = ReportStatusChangeScope(account.id, account.id)
        var returnedScope = scope
        var allowed = true
        var open = true
        var authorityChecks = 0
        var factoryCalls = 0
        var entries = 0
        var commits = 0
        val permissions = mutableListOf<ReportAdministrationPermission>()
        val scopes = mutableListOf<ReportStatusChangeScope>()
        val requests = mutableListOf<ReportStatusChangeRequest>()
        var onFactory: () -> Unit = {}
        var onEntry: suspend () -> Unit = {}
        var afterCommit: () -> Unit = {}
        val at = Instant.parse("2026-10-15T18:00:00Z")
        val request = ReportStatusChangeRequest(scope, ReportStatusChangeCommand(id(2), id(3), 1,
            ReportStatus.REPORTED, ReportStatus.IN_REVIEW, "Revisión del incidente local"), at)
        val event = ReportStatusEvent(request.command.operationId, 2, ReportStatusEventKind.STATUS_CHANGED,
            ReportStatus.REPORTED, ReportStatus.IN_REVIEW, at, account.id, request.command.reason)
        val authority = ReportAdministrationPolicy { requested, permission ->
            authorityChecks++; permissions += permission; scopes += requested
            if (!allowed) throw ReportAdministrationPermissionDeniedException()
        }
        private val sources = ReportAdministrationSources(scope, { error("Unexpected report read") },
            ReportListPageRepository { error("Unexpected report page") },
            ReportHistoryRepository { error("Unexpected report history") })
        val factory: (ReportStatusChangeScope, ReportAdministrationPolicy) -> ReportStatusChangeAccess = { captured, policy ->
            assertEquals(scope, captured)
            factoryCalls++
            onFactory()
            object : ReportStatusChangeAccess {
                override val scope = returnedScope
                override fun checkCurrent() = policy.check(captured, ReportAdministrationPermission.CHANGE_STATUS)
                override suspend fun change(request: ReportStatusChangeRequest): ReportStatusEvent {
                    entries++; requests += request; onEntry()
                    // Models the policy hook which the real SQLite implementation invokes before commit.
                    policy.check(captured, ReportAdministrationPermission.CHANGE_STATUS)
                    commits++; afterCommit()
                    return event
                }
                override suspend fun findReceipt(request: ReportStatusChangeRequest): ReportStatusChangeReceipt {
                    entries++; requests += request; onEntry()
                    return ReportStatusChangeReceipt(request.command.reportId, event)
                }
            }
        }
        fun bind(policy: ReportAdministrationPolicy = authority,
            factory: ((ReportStatusChangeScope, ReportAdministrationPolicy) -> ReportStatusChangeAccess)? = this.factory) =
            ReportAdministrationBinding(session, {
                if (!open || !sessions.isCurrent(session)) throw RevokedSession()
            }, policy, sources, factory)
    }

    private companion object {
        fun id(number: Int) = "a0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
        suspend fun denied(block: suspend () -> Any?) {
            try { block(); fail("Status access was granted") }
            catch (_: ReportStatusPermissionDeniedException) { }
        }
        suspend fun cancelled(block: suspend () -> Any?) {
            try { block(); fail("Cancelled access returned private data") }
            catch (_: CancellationException) { }
        }
    }
}
