# Probar CitizenSecurity 0.7.0

**21 de octubre de 2026 · Android 0.7.0 / código 32 · Release oficial publicada y descarga verificada.** La entrega reúne las fases 45–47 y el pulido actual, incluyendo la séptima ayuda del recorrido, las correcciones de GPS/preparación, los campos de contactos conservados al girar o ante un guardado incierto y la protección de mapas en equipos sin GLES 3. La actualización real desde 0.6.3 ya pasó en emulador propio API 37. [Cambios, identidad del archivo y comprobaciones](ENTREGA_0_7_0.md).

La [Release oficial v0.7.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.7.0), ID **419228925**, se publicó el **21 de octubre de 2026 a las 06:32:51 UTC**, sin marca de borrador ni prerelease. La APK y el archivo de comprobación se descargaron de nuevo: sus tamaños, SHA-256 y digests de GitHub coinciden. La [Release oficial anterior 0.6.3](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.3) conserva su contenido.

## Ejecutar desde Android Studio

1. Abre la raíz **citizensecurity**, donde están `settings.gradle.kts` y `gradlew.bat`. En esta PC es **C:\Proyectos\citizensecurity**; la carpeta antigua appmovile puede contener otro corte.
2. Deja terminar **Gradle Sync** y usa el wrapper y JDK configurados del proyecto. El producto compila con SDK/build-tools 37 y bytecode Java/Kotlin 17. En esta PC el JDK usado anteriormente para validar está en **C:\Users\gamel\.jdks\jdk-27+35**; comprueba el JDK elegido en Settings → Build, Execution, Deployment → Build Tools → Gradle.
3. Elige la configuración compartida **app**, en `.run/app.run.xml`. El módulo es **CITIZENSECURITY.app**. Las configuraciones `verification.location` y `verification.maps` abren comprobaciones técnicas diferentes del producto.
4. Selecciona un Android propio autorizado con **API 26 o superior** y capacidad **OpenGL ES 3.0** para usar los mapas. En un emulador comprueba la capacidad anunciada por el Android invitado, no solo la GPU de la PC. Si muestra la explicación de incompatibilidad, elige un AVD/configuración gráfica compatible y vuelve a arrancarlo; no cambies propiedades para simular una capacidad inexistente.
5. Pulsa **Run ▶**. La configuración `app` conserva el almacenamiento. Si Studio advierte una firma incompatible, cancela para revisar el archivo y su firma; conserva la instalación anterior.
6. Comprueba que ejecutaste 0.7.0/código 32 y consulta un folio previo desde la misma cuenta o acceso donde se guardó.

La entrega está integrada en **main** mediante el [PR #34](https://github.com/Spidey123134/citizensecurity/pull/34), commit `7ce0b802e22bffa9dc9a0c92ed48481bff269aaa`, también señalado por el tag `v0.7.0`. Conserva primero cualquier modificación propia. Con el directorio limpio puedes actualizar el clon así:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
```

Si Git avisa cambios propios o ramas divergentes, combínalos conservando el trabajo. No uses un restablecimiento destructivo para actualizar. Main contiene el código de 0.7.0; el tag identifica el corte publicado. Compilar o ejecutar desde Studio no publica por sí mismo una Release.

## Actualizar mediante APK

Descarga la [APK oficial citizensecurity-0.7.0-academico.apk](https://github.com/Spidey123134/citizensecurity/releases/download/v0.7.0/citizensecurity-0.7.0-academico.apk): mide **55,617,944 bytes** y tiene SHA-256 `6a312b1986fd4e6d6aff9133eade87d0847d25a80714cdd175c805f9ccd5a1d9`. Conserva el certificado académico de 0.6.3. El [archivo SHA256SUMS-0.7.0.txt](https://github.com/Spidey123134/citizensecurity/releases/download/v0.7.0/SHA256SUMS-0.7.0.txt) mide **103 bytes** y tiene SHA-256 `b9cf5ecafa8dce0de92f59f6d7453198bc827f7a57aaee92f53e49fc240241a3`. Ambas descargas quedaron verificadas el **21 de octubre de 2026 a las 06:33:32 UTC**, según `.gradle/validacion/release070-final/published-download-receipt.json`; sus hashes coinciden con los digests de `published-release.json`. Usa los datos de [Entrega 0.7.0](ENTREGA_0_7_0.md#binario-y-actualización), sin reutilizar el hash de un candidato anterior.

1. Antes de actualizar, anota un folio y la cuenta o acceso donde lo guardaste. Comprueba que puedas leerlo.
2. Instala mediante **Actualizar**, encima de la app anterior, sin desinstalar ni borrar almacenamiento.
3. Entra al mismo espacio y abre el folio anterior. Comprueba descripción, fechas, lugar, fotografía, contactos, seguimiento e historial de resultados.
4. Si Android rechaza el archivo, conserva la instalación y revisa paquete/firma. Borrar datos no es un paso para superar ese problema.

La actualización real **0.6.3 → 0.7.0 aprobó `seed063` y `verify070` en API 37**, separadas por `install -r`, con la Application productiva y firma anterior. Se conservaron los bytes de SQLite, fotografía, contactos, checkpoint y solicitud pendiente, el esquema 5, un reporte con cuatro eventos de seguimiento y una declaración de resultado. La solicitud pendiente no creó otra fila por actualizar. La comprobación en emulador no acredita por sí misma que ya se haya realizado en tu teléfono.

## Probar las mejoras de esta entrega

### Recorrido de ayuda del resultado

1. Abre un reporte guardado y desplázate a **Resultado del incidente**.
2. Pulsa el icono **? / Abrir recorrido de…** junto al título. Debe explicar resultado frente a seguimiento, quién puede declarar, revisión/confirmación e historial/recibo.
3. Avanza, vuelve, cierra y abre de nuevo. Una apertura nueva comienza por el primer paso; girar con la guía abierta conserva el paso actual.
4. Consulta también las ayudas de Inicio, Reportar, Revisión, Reportes guardados, Detalle y Mapa: son **siete temas/24 pasos** en conjunto. La guía debe permitir desplazamiento y controles alcanzables con letra grande y orientación horizontal.
5. Consultar los pasos no guarda cambios, pide ubicación, abre la cámara o envía mensajes.

### Pin y lectura GPS

1. Inicia un reporte, completa sus campos y abre la selección de punto.
2. Permite ubicación precisa y pulsa **Buscar mi ubicación**. Selecciona o ajusta el lugar del incidente en el mapa.
3. La lectura visible de esa apertura debe conservarse al ajustar el pin. Confirmar sigue exigiendo precisión y vigencia suficientes y un punto a **5 km o menos del teléfono**. Un punto lejano debe rechazarse.
4. Si sales de la pantalla o la lectura deja de ser válida, renueva GPS por un gesto. Seleccionar un pin no crea por sí mismo una lectura del dispositivo.
5. Revisa y guarda solo cuando todos los datos estén preparados. Recibir un punto válido y recibir un folio son resultados diferentes.

### Contactos, giro y recuperación de un guardado

1. En Contactos escribe nombre y teléfono sin guardar. Gira el dispositivo: ambos campos deben conservarse, sin que aparezca un contacto nuevo por ese giro.
2. Guarda mediante el botón y comprueba el teléfono normalizado en la lista. Pulsa el contacto solo si deseas abrir el marcador; el usuario decide si llama.
3. Escribe otra preparación sin guardarla, cambia de cuenta y abre Contactos. La cuenta nueva no debe heredar los campos o contactos de la anterior.
4. Si durante el uso aparece que el cambio **pudo guardarse, pero no se pudo confirmar**, los campos pendientes se conservan en la memoria de esa pantalla. Pulsa **Comprobar contactos guardados** antes de realizar otro cambio; comprobar solo vuelve a leer, sin repetir automáticamente el guardado.
5. Si esa lectura confirma el contacto exacto, la preparación correspondiente se retira. Si el contacto no quedó guardado, los campos siguen disponibles para que decidas intentar otra vez mediante el botón. Una coincidencia solo de teléfono, con otra identidad o nombre, no confirma esa alta. Los campos que hayas preparado después no deben borrarse por terminar una operación anterior.

La preparación no es una copia durable: cerrar el proceso, cancelar o terminar el espacio de la cuenta la retira; los contactos ya confirmados permanecen guardados. La comprobación de fallos y respuestas perdidas usa pruebas controladas del proyecto. No provoques corrupción, elimines archivos o borres datos de una instalación personal para simular esos casos.

### Mapa incompatible y salida útil

En un equipo que anuncie solamente GLES 2, las pantallas de seleccionar punto y consultar reportes en el mapa deben explicar la incompatibilidad antes de crear el dibujante. La selección no debe iniciar GPS o permitir confirmar; Cancelar devuelve el formulario con sus campos. Desde la consulta del mapa debe seguir disponible la lista de reportes guardados. Ese mensaje no representa un mapa cargado. [Alcance de compatibilidad](ACEPTACION_47.md#requisito-gráfico-y-error-recuperable).

### Preparación de una declaración

Necesitas una cuenta real autora del reporte o una asignación administrativa vigente en el mismo espacio. `admin/admin` y **Acceso académico sin cuenta** no conceden esos permisos.

1. Abre un folio autorizado, elige el resultado y escribe un motivo de **5–500 caracteres**.
2. Pulsa Revisar y comprueba folio, motivo y actor antes de confirmar por otro gesto.
3. En la consulta administrativa del mismo folio, actualizar su historial debe conservar la preparación o revisión de resultado. Otro folio, un error o perder el acceso deben retirarla.
4. Después de confirmar, comprueba quién declaró, rol, fecha y motivo. Corregir agrega otra declaración e identifica el resultado anterior; no borra el historial.
5. Verifica que **Cerrado** no se interprete como **Solucionado**. Son registros locales independientes, sin confirmar intervención de autoridades.

Si una declaración queda incierta, **Comprobar recibo** solo lee. Reintentar requiere otro gesto y conserva la misma operación. Si cierras completamente el proceso, vuelve a consultar lo guardado: la preparación en memoria no se recupera ni se repite automáticamente. El diario de cambios administrativos tiene su propio recorrido. [Administración local](ADMINISTRADOR_LOCAL.md).

## Registrar lo observado

La validación cerró con **1507 casos distintos aprobados**: **1461 JVM ejecutadas y 46 definiciones Android**, ejecutadas 61 veces entre las plataformas seleccionadas, sin fallos, errores u omitidos finales. Incluye la actualización desde 0.6.3, los cuatro estados de permisos en API 26 y API 37, recuperación entre procesos en API 37 y ambas rutas de rechazo del mapa en API 26/GLES 2. Un caso de interfaz necesitó corregir únicamente el desplazamiento del harness y repetirse; pasó con la misma APK del producto. El [desglose de entrega](ENTREGA_0_7_0.md#evidencia-de-esta-entrega) conserva el intento fallido y su repetición, sin sumarlos como dos casos aprobados o incluir recibos históricos en el total.

Anota modelo/Android, capacidad gráfica, versión o commit, paso, resultado esperado y observado. Conserva el tipo de acceso y el folio de prueba; evita incluir contraseñas, PIN o datos privados en capturas o publicaciones.

Distingue prueba en emulador de cámara/GNSS físicos, confirmar un punto de guardar con folio y una declaración local de recepción institucional. Las pruebas pendientes con una persona nueva, TalkBack y fabricantes se registran como pendientes hasta ejecutarlas. La app conserva datos por cuenta en ese teléfono; no hay sincronización entre instalaciones o un centro policial conectado.

**28/28 = 100% de construcción funcional local** del PDF. La entrega y sus pruebas no convierten ese porcentaje en aceptación física completa o seguridad de producción.

[Entrega 0.7.0](ENTREGA_0_7_0.md) · [Validación](VALIDACION.md) · [Guía de estudio](GUIA_ESTUDIO_APP.md) · [Prueba de aceptación](ACEPTACION_47.md) · [Entrega anterior](PROBAR_0_6_3.md)
