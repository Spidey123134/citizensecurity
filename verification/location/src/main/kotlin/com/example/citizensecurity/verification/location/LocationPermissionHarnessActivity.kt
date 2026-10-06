package com.example.citizensecurity.verification.location

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.maps.LocationPermissionViewModel
import com.example.citizensecurity.maps.PreciseLocationPermissionBinding
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/** Dueño Android aislado para instrumentación; no forma parte del APK de CITIZENSECURITY. */
class LocationPermissionHarnessActivity : ComponentActivity() {
    val instanceId: Int = nextInstance.incrementAndGet()
    val permissionModel: LocationPermissionViewModel by lazy {
        ViewModelProvider(this, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
    }
    lateinit var permissionBinding: PreciseLocationPermissionBinding
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Siempre el mismo registro, antes de STARTED, aun sin ninguna petición pendiente.
        permissionBinding = PreciseLocationPermissionBinding.forActivity(this, permissionModel)
        setContentView(FrameLayout(this))
        current = WeakReference(this)
    }

    override fun onResume() {
        super.onResume()
        permissionBinding.check()
    }

    override fun onDestroy() {
        permissionBinding.close()
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private val nextInstance = AtomicInteger()

        // Permite observar una recreación nativa con diálogo abierto, sin retener la Activity.
        @Volatile
        var current: WeakReference<LocationPermissionHarnessActivity>? = null
            private set
    }
}
