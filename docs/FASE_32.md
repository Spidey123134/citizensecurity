# Fase 32 — Ayuda inmediata y atajos del teléfono

**Implementación validada del 14 de octubre de 2026 · Unreleased.** El usuario propuso botones configurables/atajos para hacer más útil el acceso de emergencia. Esta fase implementa **E03** de [Mejoras útiles](MEJORAS_UTILES.md) con atajos estándar de Android. E04 Ajustes rápidos y E05 configuración de botones del fabricante conservan alcance separado; un mosaico no se implementa en este corte.

## Recorrido funcional

`EmergencyAccessActivity` es una pantalla pública y sin estado privado: ofrece **Ayuda inmediata** con accesos de México al marcador de 911/089. No restaura cuenta, consulta contactos/folios, solicita GPS o prepara reportes. Mantener pulsado el icono en un launcher compatible permite abrirla mediante el atajo estático, sin pasar por login.

Al tocar un número, la pantalla debe seguir visible en estado RESUMED y el dispositivo debe estar desbloqueado. Se abre `ACTION_DIAL`; el usuario revisa el número y decide llamar en la aplicación de teléfono. Abrir la pantalla, usar el atajo o recibir otra intención no inicia una llamada. Un fallo al abrir el marcador conserva el número visible y explica que no pudo abrirse.

Los números se muestran con su propósito conforme a [SSPC México](https://www.gob.mx/sspc/es/articulos/sabes-cual-es-la-diferencia-entre-los-numeros-088-089-y-911): 911 para emergencias y 089 para denuncia anónima. No se requiere redactar un reporte o conseguir ubicación antes de abrir el marcador. Las pruebas de intención no realizan llamadas ni prueban la atención del servicio.

## Acceso en Inicio y compatibilidad

La pantalla ofrece solicitar un acceso de Ayuda en la pantalla de Inicio por un gesto explícito. La comprobación de soporte y petición al launcher se ejecutan **fuera del hilo de interfaz**; la respuesta solo actualiza la pantalla si la solicitud y el gesto siguen vigentes. Se vuelve a comprobar vigencia/primer plano **después de la última consulta Keyguard potencialmente bloqueante y antes de pedir el fijado**, para no actuar si la Activity dejó de estar disponible durante esa consulta. Android **API 26–37** es el intervalo del proyecto; el launcher debe admitir fijar atajos. Si no lo admite o rechaza la petición, se muestra el resultado y permanece la ruta de Ayuda. La aceptación de la solicitud por la API significa que se pidió al launcher: **no acredita que el icono ya exista**. El usuario decide en el diálogo del sistema cuando se ofrezca.

El atajo solo contiene la ruta pública de Ayuda. No incluye propietario, sesión, números de contactos personales, GPS, folio o contenido de evidencia. Una entrada externa no puede seleccionar un contacto privado ni saltar la cuenta para abrir sus datos. No se modifica el login/loading del compañero ni se interceptan botones físicos o el SOS del sistema.

Para botones/gestos configurables, la [guía de mejoras](MEJORAS_UTILES.md#atajos-y-botones-lo-que-permite-cada-vía) recoge fuentes oficiales y limitaciones por fabricante. La fuente Motorola corresponde al **Edge 50 Fusion**, pero no se configuró/probó ese gesto físicamente en el teléfono del usuario. Inicio rápido puede abrir la app normal y pasar por splash/login; no garantiza una ruta directa a Ayuda o funcionamiento bloqueado. La aceptación física de esa configuración continúa pendiente.

## Comprobación y límites

La ejecución nativa en un emulador aislado **API 37** comprueba cuatro recorridos: apertura externa/recreación ignorando extras de teléfono, sin cambiar la sesión u obtener GPS; ambos números abren `ACTION_DIAL` únicamente después del gesto; el atajo estático publicado tiene ruta explícita y metadatos genéricos; la petición de fijado cancelada no crea un acceso ni anuncia que exista. Se intercepta únicamente el marcador para evitar llamadas; no se aprueba un icono real ni se prueba atención del servicio.

La protección por RESUMED/desbloqueo y el manejo de ausencia de aplicación receptora están implementados y revisados en código; no se atribuye a esos cuatro casos una simulación nativa específica de receptor ausente o dispositivo bloqueado. La prueba API 37 y la compilación no acreditan ejecución API 26, compatibilidad universal del launcher o configuración de botones físicos. Resultados y límites en [Validación](VALIDACION.md).

El cierre fases 31/32 aprobó **1062 casos distintos, 112 nuevos**, cero fallos/errores/omitidos finales. Incluye **9 JVM de atajos** y las **4 nativas de Ayuda**; esas cuatro se repitieron sobre el guard final posterior a Keyguard sin duplicar su recuento. Lint app sin errores/22 avisos por variante y datos sin incidencias. El total incluye otras suites del corte; su desglose está en Validación.

**19/28 = 67.9% permanece**, mismos criterios originales: el acceso rápido amplía utilidad y no completa notificaciones o recepción institucional. Código **Unreleased**, Android **0.6.0 / código 28**, sin nueva APK publicada, tag o Release.

[Creación de atajos Android](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts) · [Catálogo y siguientes propuestas](MEJORAS_UTILES.md).
