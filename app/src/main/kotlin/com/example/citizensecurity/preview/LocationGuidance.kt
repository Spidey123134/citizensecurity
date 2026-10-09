package com.example.citizensecurity.preview

import com.example.citizensecurity.maps.IncidentLocationIssue
import com.example.citizensecurity.maps.LocationPermissionAccess
import com.example.citizensecurity.maps.LocationPermissionIssue
import com.example.citizensecurity.maps.LocationPermissionState

/** Explica estados existentes; no pide permisos, obtiene GPS ni concede acceso. */
internal fun permissionGuidance(state: LocationPermissionState): String = when {
    state.requestInFlight -> "Responde al permiso de Android para continuar. Todavía no estamos buscando tu ubicación."
    state.issue == LocationPermissionIssue.PermissionCheckFailed -> "No pudimos comprobar el permiso. Vuelve a pulsar Permitir ubicación precisa."
    state.issue == LocationPermissionIssue.RequestLaunchFailed -> "Android no pudo abrir el permiso. Inténtalo de nuevo; también puedes revisar los permisos de esta app en Ajustes."
    state.access == LocationPermissionAccess.Precise -> "Permiso preciso disponible. Este permiso no inicia el GPS; la búsqueda se pide con Buscar mi ubicación."
    state.access == LocationPermissionAccess.Approximate -> "La ubicación aproximada no basta para comprobar los 5 km. Al permitir la ubicación, elige Precisa."
    state.issue == LocationPermissionIssue.Cancelled -> "El permiso se canceló. Puedes intentarlo otra vez; tu borrador sigue aquí."
    state.access == LocationPermissionAccess.Denied && state.shouldExplain -> "Sin ubicación precisa no podemos confirmar el punto. Puedes permitirla y continuar con este borrador."
    state.access == LocationPermissionAccess.Denied -> "La app no tiene permiso de ubicación. Pulsa Permitir ubicación precisa; si Android no muestra el diálogo, revisa el permiso de esta app en Ajustes."
    else -> "Primero permite ubicación precisa. Después podrás buscar el GPS del teléfono."
}

internal fun gpsGuidance(issue: IncidentLocationIssue): String = when (issue) {
    IncidentLocationIssue.TimedOut -> "El GPS no respondió a tiempo. Acércate a una ventana o a un espacio abierto y pulsa Buscar mi ubicación otra vez. El punto elegido se conserva."
    IncidentLocationIssue.LocationDisabled -> "Activa Ubicación en los ajustes del teléfono y regresa. Después pulsa Buscar mi ubicación; no se reinicia automáticamente."
    IncidentLocationIssue.Unavailable -> "No se pudo obtener el GPS. Revisa que Ubicación esté activa, busca un lugar con mejor señal y vuelve a intentarlo. El borrador se conserva."
    else -> issue.message
}
