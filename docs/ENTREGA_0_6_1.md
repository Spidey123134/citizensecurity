# Entrega 0.6.1

**Entrega oficial 0.6.1 / código Android 29 · 14 de octubre de 2026**, solicitada expresamente por el usuario. Agrupa las **fases 29–34**: ficha del incidente, contactos, accesos rápidos, recuperación durable de cambios, captura de cámara y mosaico de Ajustes rápidos. Conserva paquete, firma académica, datos, esquemas y login/loading. Las marcas Unreleased de los cortes previos son antecedentes de preparación incluidos ahora en esta entrega. [Release y APK](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.1) · [Pasos para probar](PROBAR_0_6_1.md).

## Cambios disponibles respecto a 0.6.0

- **Cámara y galería:** tomar una foto nueva o seleccionar una existente. Copia privada normalizada, vista previa/revisión antes del guardado y fotografía anterior conservada al cancelar o fallar. Aperturas por origen/token, giro sin relanzar, resultado antiguo descartado y limpieza exclusiva del temporal de cámara.
- **Ficha del incidente:** desde un folio guardado se revisa ubicación/foto opcionales, prepara un PDF paginado y después se abre el selector para compartirlo voluntariamente. Preparar no envía y un archivo incompleto no se presenta como listo.
- **Ayuda y contactos:** hasta cinco contactos manuales por cuenta y accesos al marcador de México. Abrir el marcador permite revisar el número y decidir llamar; no se lee la agenda o llama automáticamente.
- **Ayuda inmediata:** entrada pública sin login/GPS ni datos privados. Atajo del icono, petición de acceso directo y mosaico voluntario en Ajustes rápidos. El mosaico abre ayuda por toque/desbloqueo; Android confirma o rechaza su alta, con edición manual como alternativa.
- **Base de cambios de estado:** solicitud conservada en diario, consulta de su mismo recibo y reintento/reconocimiento explícitos, sin generar otra identidad ante incertidumbre. No se compone un panel administrativo ni se conceden roles.

Los datos continúan locales. La ficha es una copia organizada para compartir; no acredita autenticidad forense, recepción policial o atención de emergencias. La asignación administrativa sigue pendiente por elección del usuario; `admin/admin` es académico. El radio de 5 km y aviso de posibles repetidos de 200 m/±2 h se mantienen.

## APK y comprobación

Archivo **citizensecurity-0.6.1-academico.apk**, **55.217.523 bytes**. SHA-256: `7395a049aee8cf92324ac31f3c4e08453fb639bcf5103fc8eaa48484302815f1`. `SHA256SUMS-0.6.1.txt` acompaña la descarga. Misma firma v2 y certificado SHA-256 `0a4006a1d2ef2ad02a0dad7c51b55c0ba5ffcd096a226eed6456326b2fed3ebd`, minSdk 26/targetSdk 37; sin instrumentación, testOnly o fixtures en el APK del producto. Instalar encima de la anterior, sin desinstalar/borrar datos. La APK 0.6.0 conserva su hash y assets.

El cierre aprobó **1237 casos distintos, 307 definiciones nuevas frente a la APK 0.6.0**, cero fallos/errores/omitidos finales:

| Ejecución | Casos aprobados |
|---|---:|
| JVM dominio | 169 |
| JVM datos | 111 |
| JVM app | 794 |
| Android datos | 65 |
| Android app | 98: públicos 9, utilidad 61 y cámara/galería 28 |
| **Total** | **1237** |

Los **33 nuevos de fase 34** son 28 JVM y cinco Android, ya incluidos en ese total. Los recibos JVM de dominio/datos intactos pertenecen a la misma sesión; la app se comprobó sobre la fuente final. Lint app cero errores/22 avisos por variante y datos sin incidencias. El [cierre y sus recibos](VALIDACION.md) se separan de los cortes de preparación, sin sumar repeticiones.

Cámara externa usa un protocolo controlado en pruebas; galería, FileProvider, SQLite y diálogos de sistema son nativos. No se atribuye prueba de cámara física, GPS físico nuevo, muerte completa del proceso Android, instalación del APK como actualización o aceptación física por fabricante. La rama del mosaico API 26–33 se compila con guard específico; las pruebas nativas se ejecutan en API 37, sin ejecución física API 26.

**Avance del PDF: 19/28 = 67.9%**, mismos criterios. Cámara y ayuda amplían utilidad sin inventar puntos completados; quedan seguimiento autorizado, administración y notificaciones.
