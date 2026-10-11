package com.example.citizensecurity.report

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.domain.ReportPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** Lee exclusivamente una copia privada verificada; nunca necesita acceso a toda la galería. */
@Composable
internal fun ReportPhotoContent(photo: ReportPhoto?, modifier: Modifier = Modifier, tagPrefix: String = "photo") {
    val app = LocalContext.current.applicationContext as CitizenSecurityApplication
    var attempt by remember(photo) { mutableIntStateOf(0) }
    var bitmap by remember(photo, attempt) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(photo, attempt) { mutableStateOf(false) }
    LaunchedEffect(photo, attempt) {
        if (photo == null) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) {
                val bytes = app.reportPhotoStore.read(photo)
                ensureActive()
                val options = BitmapFactory.Options().apply {
                    inSampleSize = if (maxOf(photo.width, photo.height) > 1024) 2 else 1
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw IOException("No se pudo leer la fotografía.")
                try {
                    ensureActive()
                    decoded.asImageBitmap()
                } catch (failure: Exception) {
                    decoded.recycle()
                    throw failure
                }
            }
            bitmap = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fotografía opcional", style = MaterialTheme.typography.titleMedium)
        when {
            photo == null -> Text("Sin fotografía adjunta.", Modifier.testTag("${tagPrefix}_empty"))
            error -> {
                Text("No se pudo abrir la copia de esta fotografía. El resto del reporte sigue disponible.",
                    Modifier.testTag("${tagPrefix}_error"), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.testTag("${tagPrefix}_retry")) { Text("Volver a abrir fotografía") }
            }
            bitmap != null -> {
                Image(bitmap!!, contentDescription = "Fotografía adjunta al reporte",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp)
                        .aspectRatio(photo.width.toFloat() / photo.height).testTag("${tagPrefix}_image"))
                Text("${photo.width} × ${photo.height} · copia en este teléfono", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Abriendo fotografía…", Modifier.testTag("${tagPrefix}_loading"))
            }
        }
    }
}
