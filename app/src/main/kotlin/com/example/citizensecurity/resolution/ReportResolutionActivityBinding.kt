package com.example.citizensecurity.resolution

import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.account.ReportWorkspace
import java.util.concurrent.atomic.AtomicReference

/** Pure factories: no database or role-file reading on the UI thread. */
internal fun bindReportResolution(activity: ComponentActivity, workspace: ReportWorkspace): ReportResolutionViewModel {
    val keyguard = activity.getSystemService(KeyguardManager::class.java)
    val lease = ViewModelProvider(activity, leaseFactory(workspace, keyguard))[ReportResolutionRouteLease::class.java]
    val model = ViewModelProvider(activity, resolutionFactory(workspace, lease))[ReportResolutionViewModel::class.java]
    activity.lifecycle.addObserver(resolutionObserver(lease, model, Any()))
    return model
}

/** In the NavHost, leaving this destination revokes write access even while its Activity remains resumed. */
@Composable
internal fun rememberReportResolution(workspace: ReportWorkspace): ReportResolutionViewModel {
    val keyguard = LocalContext.current.getSystemService(KeyguardManager::class.java)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lease: ReportResolutionRouteLease = viewModel(factory = remember(workspace, keyguard) {
        leaseFactory(workspace, keyguard)
    })
    val model: ReportResolutionViewModel = viewModel(factory = remember(workspace, lease) {
        resolutionFactory(workspace, lease)
    })
    DisposableEffect(lifecycle, lease, model) {
        val owner = Any()
        val observer = resolutionObserver(lease, model, owner)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            lease.pause(owner)
        }
    }
    return model
}

private fun leaseFactory(workspace: ReportWorkspace, keyguard: KeyguardManager) = viewModelFactory {
    initializer { ReportResolutionRouteLease(workspace, keyguard) }
}

private fun resolutionFactory(workspace: ReportWorkspace, lease: ReportResolutionRouteLease) =
    ReportResolutionViewModel.factory(workspace.openResolutionRepository(lease::checkWrite), workspace::checkActive)

private fun resolutionObserver(
    lease: ReportResolutionRouteLease,
    model: ReportResolutionViewModel,
    windowOwner: Any,
): DefaultLifecycleObserver =
    object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (runCatching { lease.resume(windowOwner) }.isSuccess) model.checkAccess()
        }
        override fun onPause(owner: LifecycleOwner) { lease.pause(windowOwner) }
        override fun onDestroy(owner: LifecycleOwner) { lease.pause(windowOwner) }
    }

/** A retained lease keeps late IO from committing after pause, lock or account change. */
internal class ReportResolutionRouteLease(
    private val workspace: ReportWorkspace,
    private val keyguard: KeyguardManager,
) : ViewModel() {
    private val resumed = AtomicReference<Any?>()
    fun resume(owner: Any) { workspace.checkActive(); resumed.set(owner) }
    fun pause(owner: Any) { resumed.compareAndSet(owner, null) }
    fun checkWrite() {
        workspace.checkActive()
        val current = checkNotNull(resumed.get()) { "Vuelve a la pantalla desbloqueada para continuar." }
        check(!keyguard.isKeyguardLocked) { "Desbloquea el dispositivo para continuar." }
        check(resumed.get() === current) { "La pantalla activa cambió. Comprueba el resultado al volver." }
        workspace.checkActive()
    }
    override fun onCleared() { resumed.set(null) }
}
