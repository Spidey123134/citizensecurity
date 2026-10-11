# Probar la APK 0.5.4 en Android

La entrega **0.5.4, código Android 24** incluye fotos opcionales y una copia local del borrador para continuar después. El acceso y el login del compañero se conservan. Los reportes se guardan en este teléfono y no se envían a autoridades.

## Instalar conservando lo que ya tienes

1. Abre la [Release oficial v0.5.4](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.4) desde el teléfono.
2. En **Assets**, descarga `citizensecurity-0.5.4-academico.apk`.
3. Abre el archivo y permite la instalación desde esa fuente si Android lo pide. Instálalo **encima de la versión anterior**, con la misma firma académica. No desinstales la aplicación ni borres sus datos para actualizar.
4. Si Android rechaza la actualización, conserva la instalación anterior y registra el mensaje exacto para revisar la compatibilidad. Desinstalar perdería los reportes y fotos locales.

El APK académico está firmado con la clave de desarrollo compartida de las entregas anteriores. No es un servicio de emergencias ni una autenticación real. La migración de SQLite añade los campos opcionales de foto conservando los reportes anteriores.

## Probar foto y borrador, todavía sin GPS

1. Abre la app y entra por **Desarrollador → Nuevo reporte · guardado local**.
2. Elige un tipo y una prioridad. Para una prueba local puedes escribir una descripción como **«Prueba del formulario y de su fotografía local»** y una referencia como **«Prueba frente al mercado»**. Ajusta la fecha y hora a un momento que ya haya ocurrido.
3. Pulsa **Elegir fotografía** y elige una imagen JPEG, PNG o WebP del selector Android. Debe aparecer una vista de la copia preparada. La foto es opcional.
4. Vuelve a abrir el selector y cancélalo: la foto anterior y el texto deben conservarse. También puedes girar el teléfono y comprobar que siguen ahí.
5. Pulsa **Revisar borrador**. Comprueba tipo, prioridad, descripción, fecha/hora, referencia y foto.
6. Pulsa **Guardar borrador para continuar después**. Debe decir que el borrador se guardó en este teléfono y que todavía no es un reporte. No debe pedir GPS automáticamente ni crear un folio.
7. Sal de esa pantalla y vuelve a **Nuevo reporte**. Debe aparecer **Recuperar borrador guardado**, sin rellenar automáticamente el formulario.
8. Puedes escribir otra descripción, tocar Recuperar y pulsar **Cancelar**: debe conservar esa nueva edición. Luego toca Recuperar otra vez y confirma **Recuperar datos**. Debe recuperar la copia completa y su foto.

Guardar esta copia no necesita haber elegido un punto del mapa si la referencia es válida. Para guardar un reporte con folio sí se comprobará un punto cercano y el GPS actual.

## Probar GPS, guardar y abrir el folio

1. En el borrador recuperado, abre la selección de punto en el mapa.
2. Activa la ubicación del teléfono y concede **ubicación precisa** a la app. Pulsa la acción de buscar/probar la ubicación y espera una lectura válida; el permiso aproximado no permite confirmar el recorrido.
3. Selecciona el lugar del incidente cerca de tu ubicación actual. El límite es **5 km alrededor del teléfono**, considerando también la precisión GPS. Confirma el punto y vuelve a la revisión.
4. Si la revisión pide renovar la lectura, pulsa la acción de GPS de esa pantalla y espera el resultado. Una coordenada recuperada del borrador no sustituye esta comprobación.
5. Pulsa **Guardar reporte en este teléfono**. Debe aparecer un folio con las acciones de copiarlo, abrir el detalle y ver el punto en el mapa.
6. Abre el detalle: comprueba que descripción y foto corresponden al reporte. Sal y entra en **Reportes en este teléfono**; el mismo folio debe seguir allí.
7. Al volver a Nuevo reporte, la copia que ya se guardó debe indicar su folio y ofrecer **Abrir ese reporte**, en lugar de restaurarla como un borrador pendiente.

Si el GPS no responde, prueba donde el teléfono tenga mejor recepción y reintenta por el botón. El formulario y la foto deben mantenerse. Una imagen que no pueda validarse tampoco debe producir un reporte incompleto: el mensaje permite corregir la selección antes de volver a guardar.

## Si una operación quedó sin respuesta

**Comprobar borrador guardado** vuelve a leer la copia después de un error. **Recuperar resultado** consulta el intento de reporte; **Reintentar guardado con el mismo folio** conserva su identidad. Usa esas acciones y espera su resultado antes de iniciar otro reporte. La app mantiene los campos y el intento cuando no puede confirmar si terminó la escritura.

No hace falta desinstalar, borrar datos ni modificar cuentas. Si algo no coincide, registra la versión, el mensaje visible y el paso que estabas probando.

## Qué falta aceptar con el teléfono

La construcción del plan está en **14/28 = 50%**. Las regresiones automáticas cubren el selector, copias privadas, migración, diarios y recorrido de interfaz; la fuente GPS controlada de los tests no cuenta como prueba física. Esta sesión de prueba sirve para aceptar el recorrido final en tu Android: lectura real, punto cercano, foto, guardado y consulta del mismo folio.

La ruta alternativa del decodificador compatible con Android 26 se ejercita en Android 37; no se afirma una prueba física en Android 26. Cuentas verificadas, administración y notificaciones continúan pendientes.

Detalles del trabajo: [fase 16](FASE_16.md) y [fase 17](FASE_17.md).
