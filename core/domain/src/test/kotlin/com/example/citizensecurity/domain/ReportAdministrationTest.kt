package com.example.citizensecurity.domain

import org.junit.Assert.*
import org.junit.Test

class ReportAdministrationTest {
    private val scope = ReportStatusChangeScope(id(1), id(2))

    @Test fun constructingAccountOrAcademicScopeNeverGrantsEitherCapability() {
        listOf(scope, scope.copy(ownerAccountId = null)).forEach { captured ->
            ReportAdministrationPermission.entries.forEach { permission ->
                denied { DenyAllReportAdministrationPolicy.check(captured, permission) }
            }
        }
    }

    @Test fun aReadGrantDoesNotAuthorizeAStatusChange() {
        val policy = ReportAdministrationPolicy { requested, permission ->
            if (requested != scope || permission != ReportAdministrationPermission.READ_REPORTS)
                throw ReportAdministrationPermissionDeniedException()
        }
        policy.check(scope, ReportAdministrationPermission.READ_REPORTS)
        denied { policy.check(scope, ReportAdministrationPermission.CHANGE_STATUS) }
    }

    @Test fun anExplicitGrantDoesNotTransferToAnotherOwnerActorOrAcademicWorkspace() {
        val policy = ReportAdministrationPolicy { requested, _ ->
            if (requested != scope) throw ReportAdministrationPermissionDeniedException()
        }
        policy.check(scope.copy(), ReportAdministrationPermission.READ_REPORTS)
        listOf(scope.copy(ownerAccountId = id(3)), scope.copy(actorAccountId = id(4)),
            scope.copy(ownerAccountId = null)).forEach { other ->
            denied { policy.check(other, ReportAdministrationPermission.READ_REPORTS) }
        }
    }

    private fun id(number: Int) = "a0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private fun denied(block: () -> Unit) {
        try { block(); fail("Administrative permission was granted implicitly") }
        catch (_: ReportAdministrationPermissionDeniedException) { }
    }
}
