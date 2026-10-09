package com.example.citizensecurity.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReportMapQueryTest {
    @Test
    fun regionIncluyeSusBordesYRechazaPuntosFuera() {
        val region = MapRegion(19.0, 20.0, -100.0, -99.0)
        for (lat in listOf(19.0, 19.5, 20.0)) for (lng in listOf(-100.0, -99.5, -99.0)) {
            assertTrue(region.contains(lat, lng))
        }
        assertFalse(region.contains(18.999, -99.5))
        assertFalse(region.contains(20.001, -99.5))
        assertFalse(region.contains(19.5, -100.001))
        assertFalse(region.contains(19.5, -98.999))
    }

    @Test
    fun limitesInvalidosSeRechazanAntesDeConsultar() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            rejects { MapRegion(invalid, 90.0, -180.0, 180.0) }
            rejects { MapRegion(-90.0, invalid, -180.0, 180.0) }
            rejects { MapRegion(-90.0, 90.0, invalid, 180.0) }
            rejects { MapRegion(-90.0, 90.0, -180.0, invalid) }
        }
        rejects { MapRegion(-90.001, 90.0, -180.0, 180.0) }
        rejects { MapRegion(-90.0, 90.001, -180.0, 180.0) }
        rejects { MapRegion(20.0, 19.0, -100.0, -99.0) }
        rejects { MapRegion(-90.0, 90.0, -180.001, 180.0) }
        rejects { MapRegion(-90.0, 90.0, -180.0, 180.001) }
    }

    @Test
    fun ventanaCruzaMeridianoSinIncluirLongitudesIntermedias() {
        val region = MapRegion(-10.0, 10.0, 170.0, -170.0)
        assertTrue(region.crossesAntimeridian)
        assertTrue(region.includesAntimeridian)
        for (lng in listOf(170.0, 179.0, 180.0, -180.0, -179.0, -170.0)) {
            assertTrue(region.contains(0.0, lng))
        }
        for (lng in listOf(0.0, 169.99, -169.99)) assertFalse(region.contains(0.0, lng))
    }

    @Test
    fun bordeDeMeridianoAceptaAmbasRepresentaciones() {
        for (region in listOf(MapRegion(-1.0, 1.0, 170.0, 180.0), MapRegion(-1.0, 1.0, -180.0, -170.0),
            MapRegion(0.0, 0.0, 180.0, 180.0), MapRegion(0.0, 0.0, -180.0, -180.0))) {
            assertTrue(region.includesAntimeridian)
            assertTrue(region.contains(0.0, -180.0))
            assertTrue(region.contains(0.0, 180.0))
            assertFalse(region.contains(0.0, 0.0))
        }
        val middle = MapRegion(-1.0, 1.0, -170.0, 170.0)
        assertFalse(middle.includesAntimeridian)
        assertFalse(middle.contains(0.0, -180.0))
        assertFalse(middle.contains(0.0, 180.0))
    }

    @Test
    fun mundoCompletoYVentanaDeUnPuntoSonExplicitos() {
        val world = MapRegion(-90.0, 90.0, -180.0, 180.0)
        assertTrue(world.contains(-90.0, -180.0))
        assertTrue(world.contains(90.0, 180.0))
        assertTrue(world.contains(0.0, 0.0))
        val point = MapRegion(19.0, 19.0, -99.0, -99.0)
        assertTrue(point.contains(19.0, -99.0))
        assertFalse(point.contains(19.001, -99.0))
    }

    @Test
    fun puntosNoFinitosOFueraDelGloboNoPertenecenNiAlMundoCompleto() {
        val world = MapRegion(-90.0, 90.0, -180.0, 180.0)
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(world.contains(invalid, 0.0))
            assertFalse(world.contains(0.0, invalid))
        }
        assertFalse(world.contains(91.0, 0.0))
        assertFalse(world.contains(0.0, 181.0))
    }

    @Test
    fun consultaAcotaElLimiteYNoImponeFiltrosPorDefecto() {
        val region = MapRegion(-90.0, 90.0, -180.0, 180.0)
        val default = ReportMapQuery(region)
        assertEquals(200, default.limit)
        assertTrue(default.types.isEmpty())
        assertTrue(default.statuses.isEmpty())
        assertEquals(1, ReportMapQuery(region, limit = 1).limit)
        assertEquals(500, ReportMapQuery(region, limit = 500).limit)
        for (invalid in listOf(Int.MIN_VALUE, -1, 0, 501, Int.MAX_VALUE)) {
            rejects { ReportMapQuery(region, limit = invalid) }
        }
    }

    @Test
    fun filtrosNoCambianAlModificarSusConjuntosOriginales() {
        val types = linkedSetOf(IncidentType.FIRE)
        val statuses = linkedSetOf(ReportStatus.REPORTED)
        val query = ReportMapQuery(MapRegion(-1.0, 1.0, -1.0, 1.0), types, statuses)
        val hash = query.hashCode()
        types.clear()
        types.add(IncidentType.THEFT)
        statuses.add(ReportStatus.CLOSED)
        assertEquals(setOf(IncidentType.FIRE), query.types)
        assertEquals(setOf(ReportStatus.REPORTED), query.statuses)
        assertEquals(hash, query.hashCode())
        try {
            (query.types as MutableSet<IncidentType>).add(IncidentType.THEFT)
            fail("Los filtros de una consulta pendiente deben ser inmutables.")
        } catch (_: UnsupportedOperationException) { /* Copia inmutable. */ }
    }

    @Test
    fun igualdadDeConsultaNoDependeDelOrdenDeLosFiltros() {
        val region = MapRegion(-1.0, 1.0, -1.0, 1.0)
        val first = ReportMapQuery(region, linkedSetOf(IncidentType.FIRE, IncidentType.THEFT))
        val same = ReportMapQuery(region, linkedSetOf(IncidentType.THEFT, IncidentType.FIRE))
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertFalse(first == ReportMapQuery(region, first.types, limit = 1))
        assertFalse(first == ReportMapQuery(region, first.types, setOf(ReportStatus.CLOSED)))
    }

    @Test
    fun marcadorInvalidoSeRechazaYPaginaConservaSusDatosAlMutarLaListaOriginal() {
        val marker = ReportMapMarker("folio", IncidentType.RISK, Priority.MEDIUM, ReportStatus.REPORTED, 19.0, -99.0)
        rejects { marker.copy(latitude = Double.NaN) }
        rejects { marker.copy(longitude = 181.0) }
        val list = mutableListOf(marker)
        val page = ReportMapPage(list, true)
        list.clear()
        assertEquals(listOf(marker), page.markers)
        assertTrue(page.hasMore)
        assertEquals(ReportMapPage(listOf(marker), true), page)
        try {
            (page.markers as MutableList<ReportMapMarker>).clear()
            fail("Un resultado observado no puede cambiar fuera del modelo.")
        } catch (_: UnsupportedOperationException) { /* Copia inmutable. */ }
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("La consulta inválida debe rechazarse.") }
        catch (_: IllegalArgumentException) { /* Error de entrada esperado. */ }
    }
}
