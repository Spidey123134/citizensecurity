package com.example.citizensecurity.resolution

import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.account.ReportWorkspace
import java.util.concurrent.atomic.AtomicReference

/** Pure factories: no database or role-file reading on the UI thread. */
internal fun bindReportResolution(activity: ComponentActivity, workspace: ReportWorkspace): ReportResolutionViewModel {
    val lease = ViewModelProvider(activity, viewModelFactory { initializer {
        ReportResolutionRouteLease(workspace, activity.application.getSystemService(KeyguardManager::class.java))
    } })[ReportResolutionRouteLease::class.java]
    val model = ViewModelProvider(activity, ReportResolutionViewModel.factory(
        workspace.openResolutionRepository(lease::checkWrite), workspace::checkActive,
    ))[ReportResolutionViewModel::class.java]
    val windowOwner = Any()
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (runCatching { lease.resume(windowOwner) }.isSuccess) model.checkAccess()
        }
        override fun onPause(owner: LifecycleOwner) { lease.pause(windowOwner) }
        override fun onDestroy(owner: LifecycleOwner) { lease.pause(windowOwner) }
    })
    return model
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
