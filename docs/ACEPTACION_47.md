# Aceptación y compatibilidad — fase 47

19 de octubre de 2026. Continuación local desde revisión 46, en `development/acceptance47`. La versión Android sigue siendo 0.6.3 / código 31; la Release oficial y sus archivos conservan su identidad. Este trabajo no autoriza publicar, integrar main o subir otra APK.

## Qué se comprueba

El usuario confirmó que en esta sesión solo dispone del emulador de Android Studio. Se preparan comprobaciones en dos AVD propios aislados: `CitizenSecurity_Revision46`, API 37, y `CitizenSecurity_API26`, API 26. No se modifica el AVD del usuario ni el teléfono físico. Instalar o ejecutar instrumentación se limita al serial comprobado de cada AVD propio.

La aceptación añade un recorrido integrado entre pantallas y almacenes reales, recuperación durable en otro proceso y regresiones de contactos y acceso. Las entradas controladas se identifican como tales; pasar una prueba en un emulador no acredita sensores físicos o todos los fabricantes.

El plan original conserva **28/28 = 100% de construcción funcional local**. Esta fase no suma criterios ni asigna un porcentaje global de aceptación o de preparación para producción. [Criterios del plan](AVANCE.md) · [Validación por corte](VALIDACION.md) · [Revisión anterior](REVISION_46.md).

## Recorridos automatizados

### Reporte completo con una cuenta

`ReportEndToEndAcceptanceTest.accountCanReportReopenReadItsPhotoPreparePdfAndDeclareResultWithoutLeakingToAnotherAccount` utiliza la cuenta, workspace, SQLite, copia privada de fotografía y pantallas productivas. El registro en SQLite y la apertura de sesión son **precondiciones técnicas**: este caso no acredita el formulario nativo de registro/login. Las pruebas de acceso se registran por separado.

El recorrido introduce una fotografía controlada mediante un URI de contenido, comprueba su normalización y elimina únicamente la fuente que creó el propio test. Selecciona el punto en el mapa, obtiene una lectura del teléfono controlada por el fixture, confirma cercanía y revisa antes de guardar. Comprueba folio, reporte, foto y autor; cierra y abre otra Activity para releer el detalle. Prepara una ficha PDF, la renderiza localmente, declara un resultado y verifica el aislamiento frente a otra cuenta.

La lectura de ubicación del fixture **no es GNSS/GPS físico**. El punto y el radio se validan con el código productivo, pero no se acredita precisión de un receptor real. La importación no comprueba el selector nativo de galería ni una aplicación de cámara. El PDF se prepara sin abrir un compartidor: un monitor rechaza una apertura accidental. Por tanto, el selector, la cámara y el compartidor nativos permanecen fuera de este E2E. Abrir otra Activity tampoco equivale a matar el proceso.

### Contactos y orientación

`HelpContactsRecoveryUiTest` reproduce el formulario de contactos con cuenta y almacenamiento privado reales. Comprueba que cambiar la orientación conserve nombre y teléfono aún no guardados, sin escribirlos ni iniciar GPS. El segundo caso guarda por un gesto explícito, comprueba los campos vacíos después y verifica que una preparación posterior no pase a otra cuenta ni modifique los bytes de la primera.

La preparación de estos campos pertenece al ViewModel en memoria. Conservarla durante un giro no implica persistirla ante muerte de proceso; tampoco se copian nombre o teléfono al Bundle para conseguirlo. Las comprobaciones JVM de este cambio se registran en el cierre final.

### Recuperación en otro proceso

`ReportProcessDeathAcceptanceTest` es una aceptación optativa de dos invocaciones. Usa la `CitizenSecurityApplication` instalada, SQLite, archivos de solicitud/checkpoint y ViewModels productivos, con nombres exclusivos derivados de un UUID nuevo. No abre ni sustituye los almacenes personales de una cuenta. El test no elimina sus fixtures mientras corre el AVD; al cerrar este emulador de solo lectura se descarta su copia temporal. Los logs, hashes y recibos exportados a la PC conservan la evidencia de esa sesión.

1. `seed` guarda un reporte con foto y su solicitud/checkpoint; deja otra solicitud durable sin insertar. También confirma un resultado en SQLite y provoca una pérdida controlada de su respuesta para que el modelo del primer proceso quede en incertidumbre.
2. El operador termina el paquete y comprueba que ya no tiene PID. La siguiente invocación debe tener un PID diferente del incluido en el recibo de `seed`.
3. `verifyAfterDeath` compara los bytes/hash antes de abrir repositorios, comprueba integridad de SQLite y recupera el reporte guardado y la solicitud pendiente mediante lectura. La recuperación no crea otro reporte ni vuelve a declarar el resultado. Comprueba una sola fila, el evento correspondiente, la fotografía y los archivos durables.

Esta prueba acredita recuperación **después de un cierre controlado entre invocaciones**. No mata el proceso en mitad de una transacción, no simula pérdida de energía y no comprueba una cámara o GPS físicos. La solicitud de declaración de resultado vive en memoria: desaparece con el proceso; lo que se conserva y consulta es el resultado ya confirmado en SQL. No se añade un diario durable para esa declaración ni un reintento automático.

## Repetir la aceptación de dos procesos

Usar una copia local del código final de fase 47, JDK configurado y **solo uno de los AVD propios**. El paquete productivo debe estar instalado desde esa compilación interna. No utilizar un teléfono físico ni `adb` sin `-s`. Comprobar el nombre de AVD correspondiente al serial antes de instalar o terminar paquetes.

Desde la raíz del proyecto, configurar el serial verificado y un nonce nuevo en PowerShell:

```powershell
$phase47Adb = 'C:\Users\gamel\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$phase47Serial = 'emulator-5568'
$phase47Nonce = [guid]::NewGuid().ToString()
& $phase47Adb -s $phase47Serial emu avd name
```

El serial 5568 corresponde al AVD propio API 37 en esta sesión; API 26 usa 5570. Los seriales pueden cambiar en otra sesión: la salida del comando debe corresponder al AVD propio esperado. Usar otro UUID para cada pareja API/ejecución; no reutilizarlo si una siembra anterior dejó archivos.

Compilar el APK de instrumentación con el runner de Android que mantiene la Application real e instalar **solo ese APK de pruebas** en el AVD seleccionado:

```powershell
.\gradlew.bat '-PcitizenSecurityTestRunner=androidx.test.runner.AndroidJUnitRunner' :app:assembleDebugAndroidTest
& $phase47Adb -s $phase47Serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

No utilizar el runner habitual `ReportTestRunner` para esta pareja: sustituye la Application por fixtures. Los tests rechazan un runner o Application incorrectos. Sin `processDeathNonce`, estas dos etapas quedan omitidas deliberadamente; esa omisión no cuenta como aceptación aprobada. No ejecutar toda la clase con un solo comando: cada método necesita una invocación distinta y el cierre intermedio.

Ejecutar la siembra y conservar su salida completa. Debe terminar sin fallo de instrumentación y mostrar `PROCESS_DEATH_SEED` con PID, nonce, una fila y un evento de resultado:

```powershell
& $phase47Adb -s $phase47Serial shell am instrument -w -r -e class 'com.example.citizensecurity.acceptance.ReportProcessDeathAcceptanceTest#seed' -e processDeathNonce $phase47Nonce com.example.citizensecurity.test/androidx.test.runner.AndroidJUnitRunner
```

Solo después de confirmar ese resultado, terminar el paquete del mismo AVD y comprobar que no quede PID:

```powershell
& $phase47Adb -s $phase47Serial shell am force-stop com.example.citizensecurity
$phase47PidAfterStop = & $phase47Adb -s $phase47Serial shell pidof com.example.citizensecurity
if ($phase47PidAfterStop) { throw 'El paquete conserva un PID; no continuar la verificación.' }
```

Ejecutar la verificación con **el mismo nonce**, sin reinstalar ni borrar datos entre las etapas:

```powershell
& $phase47Adb -s $phase47Serial shell am instrument -w -r -e class 'com.example.citizensecurity.acceptance.ReportProcessDeathAcceptanceTest#verifyAfterDeath' -e processDeathNonce $phase47Nonce com.example.citizensecurity.test/androidx.test.runner.AndroidJUnitRunner
```

Debe terminar sin fallo y registrar `PROCESS_DEATH_VERIFIED`, con PID diferente al de siembra, una fila, un evento y `automaticWrites=0`. Las líneas `PROCESS_DEATH_*` se leen en `adb -s <serial> logcat -d -s System.out:I`; el recibo de siembra está en el archivo privado `files/process47_bridge_<nonce>.json`, legible mediante `run-as` en esta compilación debug. Que el comando `adb` termine no basta: revisar las aserciones y el resultado JUnit de ambas salidas. Conservar logs, nonce, plataforma y PIDs junto al recibo de validación local.

Al terminar, restaurar el APK de instrumentación habitual para Android Studio, sin la propiedad de runner:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
& $phase47Adb -s $phase47Serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Esto no publica un producto nuevo. Restaurar también los ajustes de teclado, tamaño de letra y ubicación que se hubieran cambiado en los AVD propios. Cerrar únicamente los emuladores propios comprobados, conservando sus directorios y los del usuario.

## Matriz de cierre y pendientes

### Requisito gráfico y error recuperable

La revisión de compatibilidad detectó un cierre nativo de MapLibre al abrir el mapa en el AVD API 26 que anunciaba solamente GLES 2. El [changelog del SDK 13.6.1, sección 11.0.0](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/CHANGELOG.md#1100), confirma que el dibujante requiere OpenGL ES 3.0; no basta tener Android API 26. Se conserva la dependencia actual y minSdk 26.

El manifest declara ahora GLES 3.0 como requisito. Además, `MapsConfiguration` comprueba la capacidad anunciada por Android antes de inicializar MapLibre o crear `MapView`, tanto en selección del punto como en consulta por zona. Una capacidad ausente, desconocida o inferior presenta un mensaje y permite volver al formulario conservando sus campos o consultar la lista de reportes. La selección no inicia GPS ni permite confirmar; la consulta por zona no se presenta como lista para usar. No se simula un mapa cargado ni se guardan datos para suplir la incompatibilidad.

La declaración del manifest informa a los distribuidores y permite el filtro de Google Play; **Android no comprueba por sí mismo ese requisito al instalar una APK directamente**. Por eso se mantiene también la guarda al abrir el mapa. [Documentación oficial de uses-feature](https://developer.android.com/guide/topics/manifest/uses-feature-element).

`UnsupportedMapGraphicsUiTest` añade dos casos optativos para el AVD propio API 26 que anuncie realmente GLES 2, sin sustituir la lectura de capacidad. Se ejecutan con el runner habitual de fixtures y `-e unsupportedMapGraphicsNonce UUID_NUEVO`; sin argumento o en otro entorno se omiten deliberadamente y no acreditan un pase. Comprueban mensaje, ausencia de `MapView`, acciones deshabilitadas, cero capturas GPS/escrituras y la salida útil de cada pantalla. Los resultados de esos casos y del E2E sobre GLES 3 se completarán en el cierre final.

| Área | Qué puede acreditarse en esta fase | Qué sigue fuera de esa evidencia |
| --- | --- | --- |
| Recorrido E2E | Pantallas, SQLite, foto privada, folio, PDF preparado, resultado e aislamiento | Registro/login dentro de este caso; selector/cámara/compartidor nativos; recepción por terceros |
| Ubicación | Permisos/estados Android y validación del punto con lectura controlada | GNSS físico, precisión real, interiores y comportamiento de fabricantes |
| Contactos | Preparación al girar, guardado explícito, limpieza/aislamiento entre cuentas | Conservación de campos sin guardar tras muerte del proceso |
| Recuperación durable | Dos invocaciones, PIDs distintos, bytes/recibos y ausencia de escrituras automáticas | Interrupción en mitad de commit, corte de energía o restauración de comandos de resultado en memoria |
| Compatibilidad | Casos seleccionados realmente ejecutados en API 26 y API 37, identificados en el cierre | Todos los niveles intermedios, todos los fabricantes o todos los recorridos en esas plataformas |
| Accesibilidad | Etiquetas/controles, teclado, giro y tamaño de letra que se inspeccionen o prueben | Uso real con TalkBack, navegación completa con lector de pantalla o aceptación de una persona nueva |
| Alcance local | Datos y cuentas en este teléfono/AVD, con permisos de cada espacio | Servidor, sincronización entre teléfonos, identidad policial o recepción institucional |
| Publicación | Código y evidencia local Unreleased | Nueva Release, APK pública, push o integración de main |

El servidor sigue pendiente de decidir entre un prototipo en esta PC o mantener el producto local y documentarlo para después. La pregunta separada no tiene respuesta en este corte; no se supone contratación, despliegue ni envío de datos. [Límites y mejoras útiles](MEJORAS_UTILES.md).

La confirmación manual de GPS del usuario del 11 de octubre conserva su alcance histórico: punto localizado, dentro del radio y visible en el mapa, sin versión concreta, logs o nuevo guardado físico. No se cuenta como otra ejecución de esta fase.

## Checklist humano para otra sesión

Registrar dispositivo/API, versión o commit, paso, resultado observado y cualquier fallo. Las casillas siguientes son **pendientes**, no resultados ya obtenidos:

- [ ] Una persona que no conozca la app comprende Inicio y el recorrido de ayuda sin instrucciones externas; puede cerrar y volver a abrir la guía.
- [ ] En el teléfono disponible, crear una cuenta, entrar, escribir un reporte, confirmar un punto con ubicación física y guardar hasta recibir un folio.
- [ ] Reabrir el reporte y comprobar texto, fotografía, lugar, folio e historial sin confundir guardado local con atención de una autoridad.
- [ ] Probar selección de galería y cámara nativas: aceptar, cancelar y volver; conservar los campos y la fotografía anterior cuando corresponda.
- [ ] Preparar el PDF, revisar el contenido y abrir el compartidor solo por un gesto; comprobar el archivo recibido en la aplicación elegida.
- [ ] Probar permiso denegado, ubicación aproximada si Android lo ofrece, GPS apagado y retorno desde Ajustes; no debe confirmarse un punto sin evidencia vigente.
- [ ] Probar teclado abierto, giro y letra grande en formularios y acciones inferiores; registrar modelo y cualquier control inaccesible.
- [ ] Recorrer con TalkBack nombres de campos, orden de foco, errores, tutorial y acciones de confirmar/cancelar.
- [ ] Cerrar y volver a abrir una sesión sin que otra cuenta vea fotos, reportes, contactos o preparaciones de la anterior.

No se requieren llamadas de emergencia reales, envío de SMS, datos de incidentes reales ni credenciales ajenas para estas pruebas.

## Resultados finales — 19 de octubre de 2026

**1125 casos distintos aprobados, diez definiciones nuevas; cero fallos, errores u omitidos finales.** Son 1083 JVM de app y 42 definiciones Android distintas. Las mismas definiciones ejecutadas en dos plataformas se cuentan una vez: hubo **59 ejecuciones Android finales**, no 59 definiciones nuevas. No se suman los intentos de diagnóstico, repeticiones o los cortes de otras fases.

| Ejecución final | Casos | Evidencia |
| --- | ---: | --- |
| App JVM | 1083 | Suite completa del módulo, incluidos tres casos nuevos de contactos |
| App Android API 37 / GLES 3 | 25 | Recorrido completo 1, contactos 7, acceso 3, guía 8, permisos 4 y dos etapas de proceso |
| App Android API 26 / GLES 3.1 | 18 | Recorrido completo 1, contactos 7, acceso 3, manifest del acceso rápido 1, permisos 4 y dos etapas de proceso |
| App Android API 26 / GLES 2 | 2 | Ambas rutas del mapa bloqueadas antes de crear `MapView`; cancelar conserva el formulario y la lista sigue accesible |
| Datos Android API 26 | 14 | Evidencia de ubicación 10 y fotografía 4; ejecutados el 18 de octubre dentro de esta fase, con fuentes core intactas al reanudar |

Los siete casos Android nuevos son el recorrido completo, dos de contactos, dos etapas de proceso y dos de gráficos incompatibles. La selección de datos no ejecuta el caso de ubicación simulada que requiere API 31. No se ejecuta toda la suite con etapas optativas omitidas: se seleccionan explícitamente los casos compatibles y se proporcionan sus argumentos reales.

La aceptación comprueba un reporte real con folio, foto privada normalizada, revisión, consulta tras abrir otra Activity, PDF renderizado y resultado con historial/autor; otra cuenta no obtiene ese folio ni la foto. El alta/login nativo y el giro sin restaurar contraseña pasan como pruebas separadas. En contactos, el giro conserva los campos sin escribirlos; guardar y cambiar de cuenta conservan el aislamiento. La guía también pasa con letra 130% y orientación horizontal; no se atribuye una prueba real de TalkBack.

Las dos parejas de proceso terminaron el paquete entre invocaciones y comprobaron PID vacío. API 26: **7612 → 7656**, nonce `8e192087-6e3a-461e-90b6-945e39d4009b`; API 37: **11260 → 11306**, nonce `c4b31fff-c9a2-4621-9eb1-150f35ec7bb7`. SQL, foto y archivos durables conservan bytes/hash; hay una fila y un evento de resultado, con cero escrituras automáticas al recuperar. No se mató el proceso durante un commit.

El primer AVD API 26 anunciaba GLES 2 y MapLibre se cerró dentro de su dibujante nativo, tanto con SwiftShader como con GPU host. Arrancar el AVD propio con `-gpu host -feature GLESDynamicVersion` expuso **GLES 3.1 real**, comprobado por PackageManager y SurfaceFlinger, y el recorrido completo pasó. Se volvió a arrancar con GLES 2 para ejecutar las dos pruebas del rechazo seguro; ambas pasaron sin crear vista nativa, guardar un reporte o iniciar GPS. No se falseó `ro.opengles.version` ni se rebajó la dependencia o minSdk.

El diagnóstico de Contactos conserva su fallo rojo anterior al arreglo. Un corte intermedio falló antes del producto porque el harness comprobaba el proveedor inmediatamente después de cambiar ajustes; se añadió espera acotada de cinco segundos. En API 26, preparar GPS apagado necesitó además modificar el proveedor permitido: cambiar solo `location_mode` dejó el proveedor disponible. Se corrigieron esos preparativos del emulador y se repitieron los recorridos finales; los límites productivos de ubicación permanecen intactos. Los cuatro casos de permisos pasan por separado en cada plataforma. «Solo aproximada» en API 26 describe permiso coarse sin fine preparado por PackageManager, no un selector de precisión inexistente en Android 8.

Kotlin debug/release y ensamblados internos debug/test correctos. Lint debug/release: **cero errores y 22 avisos anteriores por variante**. APK interna de 56.151.110 bytes, SHA-256 `41b3d12e9e54fde74cc5fb045e9a01aa278f582d87a43752cc5daa5530ece9fe`. Sigue siendo Android 0.6.3/código 31 y no reemplaza la descarga oficial.

Recibo ignorado `.gradle/validacion/accept47/test-proof.json`, con nombres de cada caso/plataforma, hash de logs, fuentes, APKs y PIDs. Logs finales: `accept47-graphics-final-build.log`, `accept47-ui-api37-graphics-final.log` (19 casos, 117.439 s), `accept47-ui-api26-graphics-final.log` (12 casos, 69.262 s), `accept47-permissions-api*-*.log`, `accept47-process-api*-{seed,verify}.log`, `accept47-unsupported-api26-final.log` (dos casos, 6.182 s), `accept47-data-api26.log` y `accept47-restore-runner-final-lint.log`. Los archivos de diagnóstico conservan los intentos anteriores y no forman parte de la cuenta final. La enumeración JSON es la fuente del desglose.

El runner habitual se restaura para Android Studio. Los ajustes modificados de los AVD propios se devuelven a sus valores iniciales y se cierran solo esos emuladores de solo lectura, conservando sus directorios. Sin instalaciones o permisos en teléfono físico, cambios de PIN, borrado de datos del usuario, push, tag o Release. Sensores físicos, fabricantes, una persona nueva, TalkBack y alcance del servidor siguen pendientes según la matriz anterior.
