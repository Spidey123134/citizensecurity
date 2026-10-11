# Fotografías y borrador recuperable · 0.5.4

Dos fases visibles en Desarrollador → Nuevo reporte · guardado local:

- **Fase 16 — fotografías:** elegir una foto opcional, verla en formulario/revisión y abrirla junto al folio. Copia privada que sigue disponible aunque desaparezca su origen en la galería; cancelación conserva la anterior. JPEG/PNG/WebP se verifican y normalizan sin EXIF; una copia dañada permite corregir sin guardar un reporte incompleto.
- **Fase 17 — continuar después:** guardar una copia completa desde revisión, cerrar y recuperarla por confirmación. Conserva campos y fotografía. Recuperar no inicia GPS ni inserta reportes; hace falta una lectura actual válida para guardar.
- **Correcciones de continuidad:** lectura vigente antes de recuperar, control de revisión y folio al reemplazar la copia, recarga tras respuesta incierta e identidad conservada para el mismo contenido. Una copia ya convertida en reporte abre su mismo folio.
- **Datos anteriores conservados:** migración SQLite aditiva a esquema 2 y compatibilidad con diarios sin fotografía; login/splash del compañero y versiones anteriores permanecen.

**752 casos distintos aprobados, 158 definiciones nuevas.** Debug/release compilan y lint sin errores; permanecen 22 advertencias por variante de app. Se probó selector real, SQLite, archivos privados, mapas y permisos en Android. La fuente GPS controlada de integración no acredita GPS físico final; ese recorrido queda para probarlo en tu teléfono.

**Avance: 14/28 = 50%** de construcción por los mismos hitos del PDF. Fotos completa el punto 4.2; recuperación mejora fiabilidad sin añadir criterios. Cuentas verificadas, seguimiento administrativo y avisos siguen pendientes. Los reportes se guardan en este teléfono y no se envían a autoridades.

**Instalar:** descarga `citizensecurity-0.5.4-academico.apk` en Assets y ábrelo en Android. Código 24 y misma firma académica: instalar encima de la versión anterior, sin desinstalar ni borrar datos. `SHA256SUMS-0.5.4.txt` acompaña el archivo.

[Pasos para probar en Android](https://github.com/Spidey123134/citizensecurity/blob/main/docs/PROBAR_0_5_4.md) · [Fase 16](https://github.com/Spidey123134/citizensecurity/blob/main/docs/FASE_16.md) · [Fase 17](https://github.com/Spidey123134/citizensecurity/blob/main/docs/FASE_17.md).

Para el equipo: conserva tus cambios propios, `git fetch origin`, `git switch main`, `git pull --ff-only origin main`. Main contiene el código; la Release identifica esta entrega y su APK.
