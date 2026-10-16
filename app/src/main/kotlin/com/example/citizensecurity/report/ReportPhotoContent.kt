package com.example.citizensecurity.report

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.LocalReportWorkspace
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
    val suppliedWorkspace = LocalReportWorkspace.current
    // Standalone previews/tests also capture once; production Activities always provide theirs.
    val fallbackWorkspace = remember(app, suppliedWorkspace) {
        if (suppliedWorkspace == null) app.openReportWorkspace(app.accountSession.capture()) else null
    }
    DisposableEffect(fallbackWorkspace) { onDispose { fallbackWorkspace?.close() } }
    val workspace = suppliedWorkspace ?: checkNotNull(fallbackWorkspace)
    var attempt by remember(workspace, photo) { mutableIntStateOf(0) }
    var bitmap by remember(workspace, photo, attempt) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(workspace, photo, attempt) { mutableStateOf(false) }
    LaunchedEffect(workspace, photo, attempt) {
        if (photo == null) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) {
                val bytes = workspace.readPhoto(photo)
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
            workspace.checkActive()
            bitmap = loaded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        }
    }
    OutlinedCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (photo == null) "Fotografía opcional" else "Fotografía adjunta",
                style = MaterialTheme.typography.titleMedium)
            when {
                photo == null -> Text("Sin fotografía adjunta.", Modifier.testTag("${tagPrefix}_empty"))
                error -> {
                    Text("No se pudo abrir la copia de esta fotografía. El resto del reporte sigue disponible.",
                        Modifier.testTag("${tagPrefix}_error"), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { attempt++ }, modifier = Modifier.testTag("${tagPrefix}_retry")) { Text("Volver a abrir fotografía") }
                }
                bitmap != null -> {
                    // The frame owns the height. A portrait image must not force an aspect ratio
                    // outside the parent's constraints and paint over the surrounding text.
                    Surface(modifier = Modifier.fillMaxWidth().height(240.dp)
                        .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(bitmap!!, contentDescription = "Fotografía adjunta al reporte",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().testTag("${tagPrefix}_image"))
                        }
                    }
                    Text("Imagen completa · copia en este teléfono", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Abriendo fotografía…", Modifier.testTag("${tagPrefix}_loading"))
                }
            }
        }
    }
}
