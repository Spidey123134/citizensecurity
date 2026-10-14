package com.example.citizensecurity.help

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.citizensecurity.SplashActivity
import com.example.citizensecurity.preview.PreviewTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Public numbers only: opening this activity neither reads nor changes the signed-in session. */
class EmergencyAccessActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    private var pinRequested by mutableStateOf(false)
    private var pinBusy by mutableStateOf(false)
    private var pinJob: Job? = null
    private var pendingPinResult: EmergencyPinResult? = null
    private val foreground = AtomicBoolean()
    private val pinGeneration = AtomicLong()
    private val tileGeneration = AtomicLong()
    private var tileBusy by mutableStateOf(false)
    private var tileMessage by mutableStateOf<String?>(null)
    private var pendingTileResult: TileAddResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { foreground.set(true); publishPinResult(); publishTileResult() }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> foreground.set(false)
                Lifecycle.Event.ON_DESTROY -> { foreground.set(false); pinGeneration.incrementAndGet(); tileGeneration.incrementAndGet() }
                else -> Unit
            }
        })
        // External intent data/extras are deliberately ignored. This entry accepts no telephone,
        // account or folio supplied by another app and never starts a call on creation/resume.
        setContent {
            PreviewTheme {
                Scaffold { insets ->
                    Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Ayuda inmediata", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.testTag("emergency_title"))
                        Text("Si hay peligro inmediato, abre el marcador para llamar al 911. Revisa el número y confirma la llamada en la aplicación de teléfono.")
                        Button(onClick = { openDialer("911") }, modifier = Modifier.fillMaxWidth().testTag("emergency_dial_911")) {
                            Text("Abrir marcador · 911 emergencias")
                        }
                        OutlinedButton(onClick = { openDialer("089") }, modifier = Modifier.fillMaxWidth().testTag("emergency_dial_089")) {
                            Text("Abrir marcador · 089 denuncia anónima")
                        }
                        Text("Números de México · SSPC. Este acceso no necesita iniciar sesión, preparar un reporte ni obtener ubicación.")
                        Text("Los contactos y reportes privados se consultan dentro de la app, con su acceso habitual.")
                        OutlinedButton(onClick = {
                            if (canAct()) {
                                startActivity(Intent(this@EmergencyAccessActivity, SplashActivity::class.java))
                                finish()
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("emergency_open_app")) { Text("Abrir la app") }
                        OutlinedButton(onClick = ::pinShortcut, enabled = !pinRequested && !pinBusy,
                            modifier = Modifier.fillMaxWidth().testTag("emergency_pin")) { Text("Fijar acceso en pantalla de inicio") }
                        if (pinBusy) Text("Solicitando el acceso…")
                        if (pinRequested) TextButton(onClick = { if (canAct()) pinRequested = false }) { Text("Intentar fijarlo nuevamente") }
                        Text("También puedes mantener pulsado el icono de CITIZENSECURITY y elegir Ayuda, si tu pantalla de inicio admite atajos.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = ::requestHelpTile, enabled = !tileBusy,
                            modifier = Modifier.fillMaxWidth().testTag("emergency_add_tile")) { Text("Añadir Ayuda a Ajustes rápidos") }
                        Text("También puedes bajar dos veces el panel superior, tocar Editar y añadir Ayuda. La posición y los nombres dependen de tu teléfono.",
                            style = MaterialTheme.typography.bodySmall)
                        tileMessage?.let { Text(it, Modifier.testTag("emergency_tile_message").semantics { liveRegion = LiveRegionMode.Polite }) }
                        message?.let { Text(it, Modifier.testTag("emergency_message").semantics { liveRegion = LiveRegionMode.Polite }) }
                        TextButton(onClick = { if (canAct()) finish() }, modifier = Modifier.testTag("emergency_close")) { Text("Volver") }
                    }
                }
            }
        }
    }

    private fun canAct(): Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false

    private fun openDialer(number: String) {
        if (!canAct()) return
        try {
            startActivity(HelpDialer.intent(number))
            message = "Se abrió el marcador. Revisa el número y decide si deseas llamar."
        } catch (_: ActivityNotFoundException) {
            message = "No hay una aplicación de teléfono disponible. Puedes marcar el número desde otro teléfono."
        } catch (_: Exception) {
            message = "No se pudo abrir el marcador. Puedes marcar el número desde tu teléfono."
        }
    }

    private fun pinShortcut() {
        if (!canAct() || pinJob != null || pinRequested) return
        val token = pinGeneration.incrementAndGet()
        val job = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(Dispatchers.IO) {
                    ensureActive()
                    EmergencyHelpShortcut.requestPin(applicationContext) {
                        foreground.get() && pinGeneration.get() == token
                    }.also { ensureActive() }
                }
                if (pinGeneration.get() == token && !isDestroyed) {
                    // The launcher's confirmation can pause this activity. Retain only the
                    // request result for its return; never claim creation or re-launch on resume.
                    pendingPinResult = result
                    publishPinResult()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
        pinJob = job
        pinBusy = true
        job.invokeOnCompletion { if (pinJob === job) { pinJob = null; pinBusy = false } }
        job.start()
    }

    private fun publishPinResult() {
        if (!canAct()) return
        val result = pendingPinResult ?: return
        pendingPinResult = null
        pinRequested = result == EmergencyPinResult.REQUESTED
        message = when (result) {
            EmergencyPinResult.REQUESTED -> "Solicitud enviada a la pantalla de inicio. Revisa su diálogo; esto todavía no confirma que el acceso se haya creado."
            EmergencyPinResult.UNSUPPORTED -> "Tu pantalla de inicio no permite fijar accesos desde la app. Puedes mantener pulsado su icono y buscar Ayuda."
            EmergencyPinResult.LOCKED -> "Desbloquea el teléfono y vuelve a solicitar el acceso."
            EmergencyPinResult.NOT_CURRENT -> "Vuelve a Ayuda inmediata para solicitar el acceso."
            EmergencyPinResult.FAILED -> "No se pudo solicitar el acceso. Puedes intentarlo nuevamente o buscar Ayuda al mantener pulsado el icono."
        }
    }

    private fun requestHelpTile() {
        if (!canAct() || tileBusy) return
        val token = tileGeneration.incrementAndGet()
        tileBusy = true
        tileMessage = "Solicitando el mosaico… Revisa la confirmación de Android."
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                EmergencyTileRequest.request(applicationContext,
                    canAct = { foreground.get() && tileGeneration.get() == token },
                    result = { result ->
                        lifecycleScope.launch {
                            if (!isDestroyed && tileGeneration.get() == token) {
                                pendingTileResult = result
                                publishTileResult()
                            }
                        }
                    },
                )
            }
        }
    }

    private fun publishTileResult() {
        if (!canAct()) return
        val result = pendingTileResult ?: return
        pendingTileResult = null
        tileBusy = false
        tileMessage = when (result) {
            TileAddResult.ADDED -> "Android confirmó que añadió Ayuda a Ajustes rápidos."
            TileAddResult.ALREADY_ADDED -> "Ayuda ya está en Ajustes rápidos. Puedes moverlo desde Editar."
            TileAddResult.DECLINED -> "No se añadió Ayuda. Puedes solicitarlo de nuevo o añadirlo desde Editar en Ajustes rápidos."
            TileAddResult.UNSUPPORTED -> "En este teléfono, añade Ayuda manualmente desde Editar en Ajustes rápidos."
            TileAddResult.NOT_CURRENT -> "Vuelve a Ayuda inmediata para solicitar el mosaico."
            TileAddResult.LOCKED -> "Desbloquea el teléfono y solicita el mosaico de nuevo."
            TileAddResult.FAILED -> "Android no pudo añadir Ayuda. Puedes reintentar o usar Editar en Ajustes rápidos."
        }
    }
}
