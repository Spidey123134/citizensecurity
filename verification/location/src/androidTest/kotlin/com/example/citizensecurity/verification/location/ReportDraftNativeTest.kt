package com.example.citizensecurity.verification.location

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.report.ReportDraftReview
import com.example.citizensecurity.report.ReportDraftState
import com.example.citizensecurity.report.ReportDraftViewModel
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Laboratorio sin formulario, SQLite, clave Maps ni prueba de muerte de proceso real. */
@RunWith(AndroidJUnit4::class)
class ReportDraftNativeTest {
    @Test
    fun factoryDeActivityConservaBorradorYSesionAlRecrearSinPedirGPS() {
        val initial = AtomicReference<ReportDraftViewModel>()
        val expected = AtomicReference<ReportDraftState>()
        ActivityScenario.launch(LocationFlowHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(LAB_PACKAGE, activity.packageName)
                val model = ViewModelProvider(activity, ReportDraftViewModel.factory())[ReportDraftViewModel::class.java]
                populate(model)
                model.beginLocationSelection()
                expected.set(model.state.value)
                initial.set(model)
                assertEquals(0, activity.fixture.source.requests.get())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = ViewModelProvider(activity, ReportDraftViewModel.factory())[ReportDraftViewModel::class.java]
                assertSame(initial.get(), restored)
                assertEquals(expected.get(), restored.state.value)
                assertEquals(0, activity.fixture.source.requests.get())
            }
        }
    }

    @Test
    fun registroSavedStateYParcelRecuperanCamposEnDuenoNuevoPeroNoSesionNiTokenAnterior() {
        onMain {
            val firstOwner = NativeSavedStateOwner()
            val original = firstOwner.model()
            populate(original)
            val request = original.beginLocationSelection()!!
            val expected = original.state.value.copy(locationSelectionPending = false)
            val saved = parcelRoundTrip(firstOwner.save())
            firstOwner.close()

            val restoredOwner = NativeSavedStateOwner(saved)
            try {
                val restored = restoredOwner.model()
                assertNotSame(original, restored)
                assertEquals(expected, restored.state.value)
                assertFalse(restored.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
                assertFalse(restored.cancelLocationSelection(request.token))
                assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
                val freshRequest = restored.beginLocationSelection()!!
                restored.setLocationReference("Referencia corregida después de restaurar 🌳")
                assertTrue(restored.applyLocationSelection(freshRequest.token, confirmed(NEW_LOCATION)))
                assertEquals("Referencia corregida después de restaurar 🌳", restored.state.value.location.reference)
            } finally {
                restoredOwner.close()
            }
        }
    }

    private fun populate(model: ReportDraftViewModel) {
        model.setType(IncidentType.RISK)
        model.setPriority(Priority.MEDIUM)
        model.setDescription("  Hay un árbol caído cerca del café 🌳  ")
        model.setOccurredAt(NOW.minusSeconds(60).plusNanos(123_456_789))
        model.setLocationReference("  Frente al parque de la prueba  ")
        val request = model.beginLocationSelection()!!
        assertTrue(model.applyLocationSelection(request.token, confirmed(OLD_LOCATION)))
    }

    /** Registro Android nuevo con otro ViewModelStore; no simula un GPS ni inserta un Report. */
    private class NativeSavedStateOwner(restored: Bundle? = null) : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            enableSavedStateHandles()
            controller.performRestore(restored)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun model(): ReportDraftViewModel {
            val extras = MutableCreationExtras().apply {
                set(SAVED_STATE_REGISTRY_OWNER_KEY, this@NativeSavedStateOwner)
                set(VIEW_MODEL_STORE_OWNER_KEY, this@NativeSavedStateOwner)
            }
            return ViewModelProvider(viewModelStore, ReportDraftViewModel.factory(), extras)[ReportDraftViewModel::class.java]
        }

        fun save(): Bundle = Bundle().also { controller.performSave(it) }

        fun close() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
        }
    }

    private fun parcelRoundTrip(bundle: Bundle): Bundle {
        val writer = Parcel.obtain()
        val bytes = try {
            writer.writeBundle(bundle)
            writer.marshall()
        } finally {
            writer.recycle()
        }
        val reader = Parcel.obtain()
        return try {
            reader.unmarshall(bytes, 0, bytes.size)
            reader.setDataPosition(0)
            requireNotNull(reader.readBundle(ReportDraftViewModel::class.java.classLoader))
        } finally {
            reader.recycle()
        }
    }

    private fun onMain(block: () -> Unit) {
        val error = AtomicReference<Throwable>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (failure: Throwable) {
                error.set(failure)
            }
        }
        error.get()?.let { throw it }
    }

    private fun confirmed(location: ReportLocation) = NearbyLocationConfirmation.Confirmed(location, 50.0)

    private companion object {
        const val LAB_PACKAGE = "com.example.citizensecurity.verification.location.flow"
        val NOW = Instant.parse("2026-10-06T17:00:00Z")
        val OLD_LOCATION = ReportLocation("Referencia original de selección", 19.4326, -99.1332)
        val NEW_LOCATION = ReportLocation("Otra referencia del mapa", 19.4350, -99.1320)
    }
}
