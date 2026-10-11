# Probar 0.5.6 en Android

Descarga **citizensecurity-0.5.6-academico.apk** de [Release v0.5.6](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.6). Instálala encima de la APK anterior, sin desinstalar ni borrar datos. Código Android 26 y la misma firma académica permiten conservar los reportes. Si aparece conflicto de firma, conserva los datos y consulta antes de desinstalar.

## Inicio

1. Abre la app y pulsa **Desarrollador** en el acceso existente. También se conserva el acceso académico admin/admin; no es una cuenta ni un rol real.
2. La pantalla verde debe decir **Inicio** y ofrecer **Reportar**, **Reportes guardados** y **Mapa de reportes**. No debe mostrar formulario ficticio, Probar ubicaciones ni Ejemplo de guardado.
3. Entra y regresa de los tres accesos. Ninguno debe crear un reporte, recuperar campos ni iniciar GPS por el hecho de abrirlo. La lista conserva tus folios anteriores.
4. Gira la pantalla o usa una fuente grande: puedes desplazarte y alcanzar los botones completos.

## Borrador e intento

Si conservaste una copia desde la revisión, Inicio te avisará del borrador. **Abrir borrador** lleva al formulario; restaurar requiere tu confirmación. Las coordenadas recuperadas no sustituyen el GPS reciente: antes de guardar el reporte se conserva la comprobación de ubicación dentro de 5 km.

Si hubo una interrupción al guardar, Inicio indica un **intento por comprobar**. Abre el reporte para consultar su resultado y recuperar el mismo intento/folio; el aviso no significa que falte insertarlo. Un error ofrece **Volver a comprobar** sin borrar tus archivos ni dar el diario por vacío.

## Continuar usando el reporte real

Describe un incidente, selecciona un punto cercano, solicita ubicación precisa por gesto y confirma el punto. Revisa los datos y pulsa Guardar. Si aparece un posible repetido, puedes revisar el folio anterior o confirmar que es otro incidente. El aviso sigue limitado al mismo tipo, 200 m y ±2 h; no demuestra veracidad ni consulta otros teléfonos.

El usuario ya confirmó manualmente el recorrido de punto/GPS/radio y este hito no se vuelve a exigir. Guardar y abrir un folio en el teléfono puede servir como comprobación adicional de reporte/consulta, separada de esa prueba GPS. La app no envía a autoridades ni llama a servicios de emergencia.

## Código para el compañero

Conserva tus cambios propios. Después ejecuta `git fetch origin`, `git switch main`, `git pull --ff-only origin main`, sincroniza Gradle y ejecuta app. Los nuevos accesos están en main; las Releases permiten descargar la APK y consultar cambios. No uses reset ni borres datos para actualizar.

[Fase 19](FASE_19.md) · [Avance 53.6%](AVANCE.md) · [Validación](VALIDACION.md).
