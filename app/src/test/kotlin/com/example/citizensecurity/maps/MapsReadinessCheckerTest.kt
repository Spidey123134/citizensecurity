package com.example.citizensecurity.maps

import com.google.android.gms.common.ConnectionResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class MapsReadinessCheckerTest {
    @Test fun missingKeyDoesNotConsultOrInitializeGoogleServices() {
        val checker = MapsReadinessChecker({ false }, { error("No debe consultar servicios.") })
        assertSame(MapsReadiness.MissingApiKey, checker.check())
    }

    @Test fun prerequisitesAreCheckedAgainAfterLocalConfigurationChanges() {
        var configured = false
        var services = ConnectionResult.SERVICE_MISSING
        val checker = MapsReadinessChecker({ configured }, { services })
        assertSame(MapsReadiness.MissingApiKey, checker.check())
        configured = true
        assertEquals(MapsReadiness.ServicesUnavailable(services), checker.check())
        services = ConnectionResult.SUCCESS
        assertSame(MapsReadiness.Ready, checker.check())
    }

    @Test fun outdatedServicesCannotReturnReady() = unavailable(ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED)
    @Test fun disabledServicesCannotReturnReady() = unavailable(ConnectionResult.SERVICE_DISABLED)
    @Test fun updatingServicesCannotReturnReady() = unavailable(ConnectionResult.SERVICE_UPDATING)
    @Test fun unknownServiceErrorCannotReturnReady() = unavailable(999)

    @Test fun configurationFailureDoesNotCrashOrConsultGoogle() {
        val checker = MapsReadinessChecker({ throw SecurityException("No leer detalles.") }, {
            error("No debe consultar Google tras el fallo.")
        })
        assertSame(MapsReadiness.VerificationFailed, checker.check())
    }

    @Test fun servicesFailureCanBeRetriedWithoutRecreatingTheChecker() {
        var fail = true
        val checker = MapsReadinessChecker({ true }, {
            if (fail) throw IOException("Fallo del servicio.")
            ConnectionResult.SUCCESS
        })
        assertSame(MapsReadiness.VerificationFailed, checker.check())
        fail = false
        assertSame(MapsReadiness.Ready, checker.check())
    }

    @Test fun cancellationIsNotPresentedAsAConfigurationFailure() {
        val cancellation = CancellationException("Pantalla cerrada.")
        val checker = MapsReadinessChecker({ true }, { throw cancellation })
        try { checker.check(); fail("Debe conservar la cancelación.") }
        catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }

    private fun unavailable(code: Int) {
        assertEquals(MapsReadiness.ServicesUnavailable(code), MapsReadinessChecker({ true }, { code }).check())
    }
}
