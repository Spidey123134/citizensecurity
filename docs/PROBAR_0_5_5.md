# Probar 0.5.5 en Android

Descarga `citizensecurity-0.5.5-academico.apk` desde [Assets de la Release](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.5). Instala sobre la versión anterior, **sin desinstalar ni borrar datos**. Es la misma firma académica y código Android 25; `SHA256SUMS-0.5.5.txt` permite comprobar el archivo.

1. Abre **Desarrollador → Nuevo reporte · guardado local**. Si la entrada pide acceso académico, usa `admin` / `admin`.
2. Prepara un reporte completo, selecciona un punto cercano en el mapa, autoriza ubicación precisa y confirma. Revisa los campos, actualiza tu ubicación y guarda. Conserva su folio.
3. Inicia otro reporte del **mismo tipo**, con un punto a menos de **200 metros** del anterior y una hora a menos de **2 horas** de su incidente. Usa una descripción que permita distinguir tu prueba.
4. En revisión, pulsa **Guardar reporte**. Debe aparecer el aviso con el folio anterior, distancia y hora. Abrir su detalle y volver debe conservar tu borrador y no crear otro reporte.
5. **Volver a revisar** conserva los campos y no guarda. Si realmente representa otro incidente, **Es otro incidente, guardar** pasa a la comprobación de GPS y crea su propio folio.
6. Prueba un tipo distinto o una hora fuera de esas dos horas. No debe advertir por el reporte anterior. Puede haber otras coincidencias guardadas en tu teléfono.

Una coincidencia solo orienta: no confirma veracidad ni identifica autores. La búsqueda se limita a datos de este teléfono y avisa si fue incompleta. Si tarda y sales de la pantalla, volver no debe guardar por sí solo; pulsa Guardar de nuevo.

Fotos, recuperación de la copia y reintentos con el mismo folio siguen disponibles. Recuperar coordenadas o revisar otro folio **no renueva el GPS**. Una ubicación vencida requiere actualizarla por gesto antes de guardar.

Los reportes son locales y no avisan a autoridades ni emergencias. Las pruebas automatizadas usan una fuente controlada para integrar el flujo; la aceptación física completa con tu teléfono sigue pendiente.

Para tu compañero: conserva sus cambios, `git fetch origin`, `git switch main`, `git pull --ff-only origin main`. Main contiene el código compartido y la Release conserva la entrega y APK.
