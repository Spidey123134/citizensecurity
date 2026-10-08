package com.example.citizensecurity.verification.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapLoadMonitorTest {
    private val layers = NativeMapLayers(listOf("road"), listOf("label"), listOf("building"))
    private val rendered = NativeRenderedFeatures(2, 3, 4)

    @Test
    fun estiloCargadoNoAcreditaMapaNiRenderizado() {
        val monitor = NativeMapLoadMonitor()
        monitor.onStyleLoaded(layers)
        assertEquals(NativeMapLoadState.Loading, monitor.state)
        assertTrue(monitor.styleLoaded)
    }

    @Test
    fun fotogramaParcialNoAcreditaMapaVisibleAunqueContengaObjetos() {
        val monitor = NativeMapLoadMonitor()
        monitor.onStyleLoaded(layers)
        monitor.onMapLoaded()
        monitor.onFrameRendered(false, rendered)
        assertEquals(NativeMapLoadState.Loading, monitor.state)
    }

    @Test
    fun sinObjetosDeCadaFamiliaElMapaNoSeDeclaraListo() {
        val monitor = NativeMapLoadMonitor()
        monitor.onStyleLoaded(layers)
        monitor.onMapLoaded()
        monitor.onFrameRendered(true, NativeRenderedFeatures(4, 0, 3))
        assertEquals(NativeMapLoadState.Loading, monitor.state)
        monitor.onTimeout()
        assertEquals(NativeMapLoadState.Failed(NativeMapFailure.TimedOut), monitor.state)
    }

    @Test
    fun callbacksEnOtroOrdenExigenTodosLosHechosParaListo() {
        val monitor = NativeMapLoadMonitor()
        monitor.onMapLoaded()
        monitor.onFrameRendered(true, rendered)
        assertEquals(NativeMapLoadState.Loading, monitor.state)
        monitor.onStyleLoaded(layers)
        assertEquals(NativeMapLoadState.Ready(layers, rendered), monitor.state)
    }

    @Test
    fun falloDelSdkEsTerminalAunqueLleguenCallbacksTardios() {
        val monitor = NativeMapLoadMonitor()
        monitor.onSdkFailure()
        monitor.onStyleLoaded(layers)
        monitor.onMapLoaded()
        monitor.onFrameRendered(true, rendered)
        monitor.onTimeout()
        assertEquals(NativeMapLoadState.Failed(NativeMapFailure.SdkFailure), monitor.state)
    }

    @Test
    fun estiloIncompletoFinalizaLaEspera() {
        val monitor = NativeMapLoadMonitor()
        monitor.onStyleLoaded(layers.copy(buildings = emptyList()))
        assertEquals(NativeMapLoadState.Failed(NativeMapFailure.RequiredLayersMissing), monitor.state)
    }

    @Test
    fun timeoutNoSeConvierteEnExitoPorUnaRespuestaTardia() {
        val monitor = NativeMapLoadMonitor()
        monitor.onTimeout()
        monitor.onStyleLoaded(layers)
        monitor.onMapLoaded()
        monitor.onFrameRendered(true, rendered)
        assertEquals(NativeMapLoadState.Failed(NativeMapFailure.TimedOut), monitor.state)
    }

    @Test
    fun cerrarVistaInvalidaCallbacksYTimeoutPendientes() {
        val monitor = NativeMapLoadMonitor()
        monitor.close()
        monitor.onStyleLoaded(layers)
        monitor.onMapLoaded()
        monitor.onFrameRendered(true, rendered)
        monitor.onSdkFailure()
        monitor.onTimeout()
        assertEquals(NativeMapLoadState.Closed, monitor.state)
    }
}
