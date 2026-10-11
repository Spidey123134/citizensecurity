package com.example.citizensecurity.domain

import java.util.UUID

/**
 * Referencia a un JPEG normalizado en almacenamiento privado; no contiene una URI externa
 * ni metadatos EXIF. La capa de archivos comprueba los bytes, hash y dimensiones reales
 * antes de insertar: esta referencia por sí sola no acredita que la foto exista.
 */
data class ReportPhoto(
    val id: String,
    val sha256: String,
    val byteCount: Long,
    val width: Int,
    val height: Int,
) {
    init {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "La fotografía necesita un UUID completo y canónico."
        }
        require(HASH_PATTERN.matches(sha256)) { "El hash de la fotografía necesita 64 caracteres hexadecimales minúsculos." }
        require(byteCount in 1..MAX_BYTES) { "La fotografía debe ocupar entre 1 byte y 6 MiB." }
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "Las dimensiones de la fotografía deben estar entre 1 y 2048 píxeles."
        }
    }

    companion object {
        const val MAX_BYTES: Long = 6L * 1024 * 1024
        const val MAX_DIMENSION: Int = 2048
        private val HASH_PATTERN = Regex("[0-9a-f]{64}")
    }
}
