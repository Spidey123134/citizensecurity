# Probar CitizenSecurity 0.6.3

**18 de octubre de 2026 · Android 0.6.3 / código 31 · binario y actualización validados; publicación pendiente.** El APK final ya conserva la firma anterior y aprobó actualización real desde 0.6.2 en un emulador propio. La publicación y descarga de la Release se confirmarán por separado. [Cambios, identidad del archivo y límites](ENTREGA_0_6_3.md).

## Ejecutar desde Android Studio

1. Abre la carpeta raíz **citizensecurity**, donde están `settings.gradle.kts` y `gradlew.bat`. En esta PC corresponde a **C:\Proyectos\citizensecurity**; la carpeta antigua appmovile no contiene necesariamente el corte actual.
2. Deja terminar **Gradle Sync**. El proyecto usa su wrapper Gradle 9.8, SDK/build-tools 37 y bytecode Java/Kotlin 17. En esta PC el JDK usado para validar está en **C:\Users\gamel\.jdks\jdk-27+35**; puedes elegirlo en Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK.
3. En la barra superior selecciona la configuración **app**, compartida en `.run/app.run.xml`, y un emulador o teléfono propio autorizado. Si Studio pide el módulo, elige **CITIZENSECURITY.app**. Las configuraciones `verification.location`/`verification.maps` ejecutan laboratorios técnicos diferentes del producto.
4. Pulsa **Run ▶**. Si ya tienes la app con la firma académica, conserva los datos e instala encima. Si Studio advierte una firma incompatible, cancela y revisa la firma; no aceptes borrar la instalación para superar el mensaje.
5. Comprueba la versión del APK que ejecutaste y consulta desde el mismo acceso un folio anterior. Compilar o ejecutar desde Studio no publica una Release de GitHub.

Para obtener cambios compartidos, conserva primero tus modificaciones propias. Con el directorio limpio y la entrega ya integrada en main, usa:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
```

Si Git avisa cambios o ramas divergentes, detente para combinarlos conservando tu trabajo. No uses un restablecimiento destructivo para actualizar. En esta preparación la integración/publicación se documenta al completarse; ejecutar comandos no sustituye esa comprobación.

## Actualizar mediante APK

Cuando se confirme la publicación, descarga **citizensecurity-0.6.3-academico.apk** y **SHA256SUMS-0.6.3.txt** desde la Release 0.6.3 enlazada en la entrega. El APK final mide **56,151,110 bytes** y su SHA-256 es `0109149d9f9e6ea1a6883b46fe6915dde7bba9e20fba45c5219dfea6892d1780`.

Anota antes un folio y la cuenta o acceso donde lo guardaste. Instala mediante **Actualizar**, sin desinstalar ni borrar datos, e ingresa al mismo espacio. Comprueba campos, foto, contactos e historial previos. La actualización 0.6.2 → 0.6.3 ya aprobó sus dos etapas en emulador propio, conservando bytes anteriores y comprobando esquema 4 → 5. Esta guía permite registrar una aceptación física nueva; no afirma que ya se haya ejecutado en tu teléfono.

## Consultar el recorrido guiado

1. En Inicio busca el icono **? / Abrir recorrido de…**. La guía debe abrirse solo al pulsarlo.
2. Recorre Siguiente y Anterior; comprueba progreso y usa Cerrar o Listo para volver a la pantalla.
3. Abre otra vez: comienza por su primer paso. Si giras con la guía abierta, conserva el paso actual.
4. Comprueba también Reportar, Revisar reporte, Reportes guardados, Detalle y Mapa: explican la pantalla actual. Puedes probar letra grande y horizontal; el texto y las acciones deben poder recorrerse.
5. Abrir la guía no debe guardar, pedir ubicación o abrir cámara/galería. Los gestos que explica se realizan después en la función correspondiente.

Esta versión incluye seis temas y 20 pasos; las mejoras posteriores del tutorial se documentarán por separado.

## Declarar si un incidente se solucionó

Necesitas una **cuenta local real** y un reporte creado con ella, o una asignación administrativa vigente dentro del mismo espacio. `admin/admin` y Desarrollador no obtienen permiso para declarar. No hay datos o declaraciones de ejemplo.

1. En **Reportes guardados**, abre un reporte propio y desplázate a **Resultado del incidente**. Sin una declaración previa debe mostrar **Sin confirmar**, incluso si el seguimiento está Cerrado.
2. Elige **Solucionado** o **Sigue sin solucionarse** y escribe un motivo válido de 5–500 caracteres.
3. Pulsa **Revisar** y comprueba folio, resultado, motivo y quién declarará. Revisar o girar todavía no debe agregar un evento.
4. Pulsa **Confirmar** mediante otro gesto. Comprueba recibo e historial: resultado anterior/nuevo, UUID de cuenta, rol, fecha y motivo.
5. Para corregirlo elige otro resultado y repite revisión/confirmación. El historial debe conservar la declaración anterior. **Sin confirmar** puede ser una corrección nueva; no borra las anteriores.
6. Comprueba que el seguimiento administrativo no haya cambiado por declarar el resultado. Cerrado y Solucionado siguen siendo conceptos independientes.

Ante incertidumbre, **Comprobar recibo · solo lectura** consulta el resultado sin repetirlo. Si después decides **Reintentar la misma declaración**, conserva la operación original; no prepares otra para resolver la anterior. La solicitud vive en memoria: cerrar completamente el proceso obliga a consultar lo realmente guardado, sin reintento automático.

Para el recorrido administrativo usa la [guía de administración](ADMINISTRADOR_LOCAL.md): registrar `admin_local` con contraseña propia y confirmar al propietario mediante Android no equivale a tener acceso a otras cuentas. No publiques contraseña, PIN o datos privados al registrar el resultado.

## Registrar lo observado

Anota modelo/Android, versión, cuenta o tipo de acceso, folio conservado, foto/contactos legibles, guía utilizada y declaración con su historial. Distingue confirmar un punto GPS de guardar con folio, un estado administrativo de su resultado declarado y una declaración local de una verificación institucional.

**28/28 = 100% de construcción funcional local** del PDF; la aceptación física y la seguridad de producción se comprueban aparte. [Entrega](ENTREGA_0_6_3.md) · [Validación](VALIDACION.md) · [Funciones anteriores](PROBAR_0_6_2.md).
