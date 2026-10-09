# Fase 12 — vista previa de nuevo reporte

**Registro histórico de su corte local.** Este código de interfaz quedó incorporado a la entrega oficial [0.5.1](ENTREGA_0_5_1.md), por la instrucción posterior de trabajar con Releases. Los resultados y restricciones siguientes corresponden al corte original.

Estado: local, Unreleased, 9 de octubre de 2026. Rama development/fase-12-interfaz-mapa. Base oficial 0.5.0, sin commit ni publicación nuevos.

## Alcance autorizado

El usuario pidió avanzar e iniciar la interfaz, permitiendo usar la del primer borrador. Se reutilizó la organización de NewReportScreen y la paleta de ui/theme del respaldo EntregaApp/appmovile. No se copiaron su lógica antigua de permisos, autenticación o almacenamiento. Se conserva el login y la navegación de entrada del compañero.

El formulario Compose permite elegir tipo y prioridad, describir lo ocurrido, indicar fecha/hora y referencia, abrir el mapa, tocar un punto y revisar campos. La vista MapLibre/OpenFreeMap Liberty ofrece calles, controles explícitos de permiso/GPS y confirmación con las reglas actuales de radio 5 km, precisión, vigencia y ubicación no simulada. La cámara inicial en Ciudad de México es una referencia visual; no representa el GPS del teléfono ni un reporte.

El borrador se conserva al ir al mapa, cancelar y recrear la Activity. También se conserva texto rechazado por longitud para poder corregirlo sin truncarlo. Se descartan callbacks de vistas sustituidas y se cierra la vida de MapView al abandonar la pantalla. La revisión valida los campos; no envía ni escribe SQLite, no avisa a emergencias y no activa consulta, fotos o guardado real.

## Probar la vista

Ejecuta la variante debug desde Android Studio. Entra por el acceso de desarrollador existente y pulsa **Nuevo reporte · vista previa**. Abre el mapa, permite ubicación precisa y después pulsa **Buscar mi ubicación**; conceder permiso no captura por sí solo. Selecciona un lugar y confirma. El modelo rechaza un punto distante, GPS vencido/impreciso o simulado. Sin GPS válido la confirmación está bloqueada.

El APK debug tiene nombre 0.5.0-fase12 y código 20. La Activity y tema nuevos solo existen en app/src/debug. La variante release no incorpora la pantalla y mantiene el botón oculto. No se ha instalado sobre la aplicación del teléfono del usuario.

## Validación

Compilación debug/release, APK de instrumentación y lint completos aprobados. Las pruebas JVM existentes de app se ejecutaron en el primer pase: 273 casos aprobados; las ejecuciones UP-TO-DATE posteriores no se suman como nuevas. Las seis pruebas instrumentadas añadidas se ejecutaron y aprobaron en Android API 37: campos obligatorios, conservación del formulario al recrear, abrir/cancelar/reabrir mapa, conservación de texto rechazado, recreación con mapa abierto y toque real con pin renderizado y confirmación bloqueada sin GPS. Se comprobaron nombres del mosaico real y capturas visuales, además de que las bases existentes no cambian de nombre o contenido. Compilar el APK de pruebas no se cuenta como ejecución.

La validación visual usa exclusivamente una instancia de emulador de prueba en modo read-only, sin instalar sobre el teléfono. El AVD nuevo no terminó de arrancar; el AVD existente en modo de prueba sí consiguió conexión ADB. El acceso completo desde Splash/login → Desarrollador → Nuevo reporte · vista previa también se comprobó manualmente en la instancia de prueba. La aprobación con GPS físico de esta nueva pantalla queda pendiente.

## Pendientes y plan

Corresponde al acoplamiento visual de reporte y geolocalización (4.2/4.3/4.5 del plan), sin alterar pesos ni marcar terminada la aplicación. Avance acreditado 10/28 = 35.7%. Faltan revisión del diseño por el usuario, permiso aproximado/denegado y GPS físico dentro de este flujo, aceptación completa, y decidir explícitamente la activación de guardado/consulta/fotos. Main y Release oficial 0.5.0 se mantienen; no se autoriza publicar este incremento.

### Cierre y evidencia local

Compilación final debug/release y APK de pruebas aprobada. Lint: cero errores y 19 advertencias anteriores por variante. Firma debug v2 válida; nombre Android 0.5.0-fase12, código 20. La clase de la pantalla solo está en debug; la suite de instrumentación no está en los APK del producto. El APK oficial 0.5.0 conserva su SHA-256 y los archivos anteriores permanecen.

Se corrigió el estado listo del mapa: ahora espera estilo, mosaicos y fotograma completo, en lugar de confundir estilo cargado con calles visibles. La prueba espera etiquetas reales y el pin renderizado antes de capturar. También se corrigió pérdida de texto rechazado al volver del mapa y se cerró el recurso de mapa aunque falle su construcción. Se trasladó el botón de vista previa al inicio de Desarrollador para que sea visible sin recorrer el formulario anterior.

El primer pase de instrumentación falló por Espresso antiguo transitivo; 3.7.0, alineado con AndroidX Test actual, evita la llamada reflexiva retirada de InputManager, según las [notas oficiales de AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0). El segundo pase detectó una suposición incorrecta del laboratorio: la copia de prueba ya tenía reports.db de 0.3.1. Se sustituyó la expectativa de base vacía por hashes de cada archivo antes y después de cada caso, sin limpiar ni alterar los datos. Las repeticiones de los mismos seis casos no aumentan el recuento.

Archivos ignorados en .gradle/validacion: fase12-cierre-final.log, fase12-ui-cierre.log, fase12-formulario-cierre.png, fase12-mapa-cierre.png, citizensecurity-fase12-preview-final.apk y cierre-fase12.json. Son evidencia local, no assets de una Release. GPS físico/permiso aproximado o denegado en esta nueva pantalla siguen pendientes; no se afirma aceptación productiva completa.
