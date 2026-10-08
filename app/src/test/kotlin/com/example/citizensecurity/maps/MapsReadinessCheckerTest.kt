package com.example.citizensecurity.maps

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/** Inicialización del SDK, sin simular un estilo descargado ni un mapa renderizado. */
class MapsReadinessCheckerTest {
    @Test fun initializesWithoutAnApiKeyOrGoogleServicesContract() {
        var initialized = 0
        val checker = MapsReadinessChecker { initialized++ }
        assertSame(MapsReadiness.Ready, checker.check())
        assertEquals(1, initialized)
    }

    @Test fun sdkInitializationFailureDoesNotReturnReady() {
        val checker = MapsReadinessChecker { throw IOException("Detalles privados de inicialización.") }
        assertSame(MapsReadiness.VerificationFailed, checker.check())
    }

    @Test fun initializationCanBeRetriedAfterFailure() {
        var fails = true
        val checker = MapsReadinessChecker { if (fails) throw SecurityException("Fallo local.") }
        assertSame(MapsReadiness.VerificationFailed, checker.check())
        fails = false
        assertSame(MapsReadiness.Ready, checker.check())
    }

    @Test fun aMissingNativeLibraryIsAnUnavailableRenderer() {
        val checker = MapsReadinessChecker { throw UnsatisfiedLinkError("ABI incompatible.") }
        assertSame(MapsReadiness.RendererUnavailable, checker.check())
    }

    @Test fun aFailedNativeClassInitializationIsAnUnavailableRenderer() {
        val checker = MapsReadinessChecker { throw NoClassDefFoundError("Inicialización JNI fallida.") }
        assertSame(MapsReadiness.RendererUnavailable, checker.check())
    }

    @Test fun cancellationIsNotPresentedAsAConfigurationFailure() {
        val cancellation = CancellationException("Pantalla cerrada.")
        val checker = MapsReadinessChecker { throw cancellation }
        try { checker.check(); fail("Debe conservar la cancelación.") }
        catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }

    @Test fun fatalErrorsAreNotSwallowedAsARetryableMapFailure() {
        val fatal = AssertionError("Error fatal del proceso.")
        val checker = MapsReadinessChecker { throw fatal }
        try { checker.check(); fail("Debe propagar el error fatal.") }
        catch (actual: AssertionError) { assertSame(fatal, actual) }
    }
}
