# Empezar a trabajar en el repositorio

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](FASE_7.md), [acoplamiento](ACOPLAR_INTERFAZ.md) y [estado por hitos](AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

## Obtener la actualización y seguir desde main

Para trabajar en el código de la actualización usa **`main`**; la [Release](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) identifica el corte y su APK. Comprueba primero `git status`. Si tienes cambios propios, consérvalos en tu rama antes de cambiar o actualizar. Con el directorio de trabajo limpio:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/interfaz-mapa
```

Usa una rama nueva para otra función o vuelve a tu rama existente si continúas ese trabajo. `--ff-only` evita crear una integración automática; si no puede avanzar, coordina los cambios en vez de sobrescribirlos. Después abre la carpeta completa en Android Studio, sincroniza Gradle y comprueba **`versionName = "0.4.0a"` y `versionCode = 19`** en `app/build.gradle.kts`. Cada PC configura su `local.properties`, JDK 27 y clave Maps local; estos archivos/configuraciones personales no se copian desde la Release.

El compañero mantiene sus interfaces y login. Para conectar nuestra lógica, empieza con [el borrador editable](FASE_7.md) y [Acoplar interfaz](ACOPLAR_INTERFAZ.md): la versión incorpora contratos y pruebas, pero no activa guardado real, consulta ni un mapa sin clave. Usa su formulario para representar esos estados y coordina una función a la vez.

## Antecedentes de los cortes locales

Los siguientes bloques conservan los cortes anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el [flujo de ubicación en Android](FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. El módulo técnico `verification/location`, `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas. Conserva sus tres variantes de diálogo nativo y añade `flow` con paquete propio y fuentes controladas. Las lecturas sintéticas pertenecen solo al laboratorio y no prueban GPS físico ni mapa visible. El compañero conserva pantallas, login, controles y presentación visual.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**, con **18 definiciones nuevas**. Los dos de GPS comprueban registro nativo con cancelación antes del callback y rechazo de una `Location` marcada como simulada mediante una fuente controlada; no obtuvieron un fix físico. Debug/release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`** como evidencia previa, sin nueva ejecución. [Validación](VALIDACION.md) separa comandos, repeticiones y límites.

Como antecedente, **0.3.9 aprobó 121 JVM de app y tres flujos nativos de permiso**, compilaciones debug/release y lint sin errores con 13 advertencias anteriores. Las 47 JVM nuevas pertenecen a 0.3.8. Conceder permiso no inicia GPS ni guarda reportes. GPS físico, mapa renderizado, conexión de los controles y clave Google Cloud siguen pendientes. Guardado real y consulta conservan sus accesos ocultos. En aquel corte no había acciones de publicación autorizadas. Avance: **7/28 = 25%**.

Repositorio compartido: [Spidey123134/citizensecurity](https://github.com/Spidey123134/citizensecurity). El `main` remoto observado el 3 de octubre está en [“cambiar interfaz”, `b665a56a`](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), del compañero, todavía **0.4.0 / código 7**. La [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0) sigue publicada y su etiqueta permanece en `08ab2d5`, un commit anterior. La integración y publicación de esa Release se hicieron sin instrucción humana; el usuario pidió terminarla y explicar los cambios. Por indicación posterior se conserva lo publicado.

La preparación local **0.3.11 / código 18** conservó el trabajo anterior sobre `08ab2d5`. En ese corte no estaba disponible para el compañero por GitHub: un `pull` del main entonces observado no entregaba esas modificaciones. La actualización 0.4.0a cierra esa preparación y su publicación está expresamente autorizada; [Versiones](RELEASES.md) registra su validación y enlaza el estado de GitHub. Terminar, probar o pulir por sí solo sigue sin autorizar publicaciones futuras.

El cambio remoto `b665a56a` añade `activity_createaccount.xml`, modifica `activity_main.xml` y `activity_released_tools.xml` y añadió un descriptor Daemon JVM 25. No cambió Kotlin, las versiones Android ni los contratos de dominio o SQLite. La creación de cuenta es solo visual; el login real continúa pendiente. Su integración autorizada en 0.4.0a conserva los controles del ejemplo en memoria, ausentes en el XML remoto, y coordina el daemon a **JDK 27**, con criterio generado de Gradle. Se comprueba el conjunto antes de subirlo.

## Acceso y primera copia

El acceso de escritura de `vazdavr-sudo` se comprobó el 2 de octubre de 2026. Puede subir sus ramas usando esa cuenta. El repositorio público permite leer y clonar el código.

Para crear una copia nueva:

```powershell
git clone https://github.com/Spidey123134/citizensecurity.git
cd citizensecurity
git switch -c feature/interfaz-inicial
```

Si ya tienes una copia, comprueba `git status` y guarda tus cambios en tu rama antes de actualizar. Con el directorio de trabajo limpio:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/interfaz-inicial
```

Usa un nombre de rama nuevo si `feature/interfaz-inicial` ya existe; si estás continuando esa tarea, vuelve a su rama existente.

## Abrir y comprobar la base

1. Abre la carpeta `citizensecurity` completa en Android Studio.
2. Instala JDK 27 para Gradle y Android SDK Platform 37.0 / Build Tools 37.0.0. La actualización 0.4.0a coordina `gradle-daemon-jvm.properties` a Java 27; el bytecode Android permanece en Java/Kotlin JVM 17.
3. Configura la ruta del SDK de tu PC cuando Android Studio lo solicite. `local.properties` y los ajustes locales del IDE quedan fuera de Git.
4. Sincroniza Gradle. Se conservan `SplashActivity`, `activity_splash.xml`, el login visual y el trabajo remoto del compañero. La actualización declara `0.4.0a` / código 19; [Versiones](RELEASES.md) registra su validación y enlaza el estado de GitHub. Para comprobar permisos y ubicación sigue [Pruebas de permisos Android](PRUEBAS_PERMISOS_ANDROID.md) y [Flujo de ubicación Android](FLUJO_UBICACION_ANDROID.md): usa los paquetes técnicos del laboratorio, sin ejecutar `app` sobre la aplicación del usuario para validar pruebas. El guardado real y toda consulta siguen sin acceso visual.

**Desarrollador · funciones básicas** y **admin / admin** abren **Funciones básicas · 0.3.1**. El título identifica las herramientas heredadas de la base, aunque el APK sea 0.4.0a. Conservan recepción, validación, coordenadas y solo **Ejemplo de guardado** con datos ficticios fijos en SQLite en memoria. No utiliza el formulario ni la base productiva. El guardado real y toda consulta quedan ocultos. El acoplamiento del XML del compañero conserva los controles de este ejemplo. Véase [Funciones visibles](FUNCIONES_PUBLICADAS.md).

El primer paso interno de 0.3.2 preparó ubicación provisional, confirmación y cancelación en `core/domain`. 0.3.3 añadió cercanía dentro de **5 km alrededor del teléfono**, según la elección del usuario, dentro de **fase técnica 6** y los apartados **4.3 y 4.5 del PDF**. [Fase 6](FASE_6.md) y [Proximidad](PROXIMIDAD_REPORTES.md) explican los contratos; [Avance](AVANCE.md) conserva **7/28 = 25%**. El SDK y la fuente de captura están incorporados desde 0.3.4; el puente de permisos llegó en 0.3.8 y sus tres flujos nativos aislados aprobaron en 0.3.9. La preparación 0.3.10 conecta esos permisos con renovación y confirmación en el laboratorio, usando lecturas controladas. GPS físico y mapa renderizado siguen pendientes. La vista del mapa, el pin, sus controles y el login corresponden al compañero.

La futura pantalla usa `confirmNearby` con evidencia reciente y precisa del teléfono; `confirm()` comprueba solo geometría. La evidencia proviene del proveedor del dispositivo y pasa por `application.phoneLocationEvidence`, separada del marcador. El repositorio productivo protegido comprueba nuevamente la regla antes de escribir: sin evidencia válida rechaza el guardado. El incremento inicial 0.3.3 no añadió captura GPS ni permisos al manifiesto; esos componentes se incorporaron después y todavía requieren conexión y comprobación del flujo real. No se deben alimentar datos ficticios para habilitar guardado real. El ejemplo fijo en memoria conserva su funcionamiento sin GPS.

Para ejecutar los comandos desde una terminal de Windows, configura también `JAVA_HOME`. Sustituye la ruta del ejemplo por la de tu propio JDK 27; el ajuste del JDK dentro del IDE no configura esta variable de la terminal. Conserva el descriptor coordinado de Java 27 de la actualización.

```powershell
$env:JAVA_HOME = 'C:\ruta\a\tu\jdk-27'
.\gradlew.bat :core:domain:recibirReporte --console=plain
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
```

El primer comando de Gradle muestra cómo el contrato recibe los datos del reporte. El segundo comprueba los modelos de la aplicación, las reglas existentes, lint y compilación. La lógica conservada de consulta 0.4 se puede comprobar técnicamente con un emulador o teléfono conectado; este comando no habilita una pantalla:

```powershell
.\gradlew.bat :core:data:consultarReporte --console=plain
```

La tarea ejecuta la suite instrumentada y muestra la recuperación por folio y el listado después de reabrir una base de prueba temporal. Exporta la evidencia a `core/data/build/reports/fase4/<dispositivo>/consulta.json`, fuera de Git. La prueba directa de persistencia usa `:core:data:connectedDebugAndroidTest`; las comprobaciones de cada entrega están en [Validación](VALIDACION.md).

La pantalla de carga tiene su recurso de layout desde el commit `e18e636`. La autenticación y la conexión visual del reporte siguen pendientes del compañero; las tareas de `core/domain` y `core/data` permiten comprobar nuestra parte.

## Dónde trabaja cada uno

| Área | Responsable | Punto de entrada |
| --- | --- | --- |
| Interfaces, menú y navegación | `vazdavr-sudo` | `app/src/main/kotlin/com/example/citizensecurity/MainActivity.kt`, `app/src/main/res/layout/` y recursos visuales |
| Login | `vazdavr-sudo` | Se define como su propia función antes de conectar cuentas o servicios |
| Reglas y modelos del reporte | Nuestra parte de lógica y datos | `core/domain/src/main/kotlin/com/example/citizensecurity/domain/` |
| Guardado y consultas SQLite | Nuestra parte de lógica y datos | `core/data/src/main/kotlin/com/example/citizensecurity/data/` |
| Dependencias, manifiesto y conexión entre capas | Ambos | Se coordinan al conectar la interfaz |

El formulario futuro editará `ReportDraftViewModel` y usará el puente de ubicación conservando los campos al ir y volver del mapa, según [Fase 7](FASE_7.md) y [Acoplar interfaz](ACOPLAR_INTERFAZ.md). El guardado futuro usará `ReportViewModel` y la consulta `ReportQueryViewModel`, ambos con el repositorio compartido de `CitizenSecurityApplication`; la pantalla no abre ni cierra SQLite. El compañero puede preparar el diseño, pero debe dejar sin conectar guardado real y consulta y mantener sus accesos ocultos hasta una instrucción precisa.

El ejemplo visible usa **`SaveExampleViewModel.runExample()`**, que llama a **`TemporaryReportSaveExample.run()`** sin un borrador. Su resultado se presenta en un campo separado de recepción/validación, permanece al rotar y se reutiliza sin otra inserción hasta salir. No debe leer los campos del formulario ni cambiar al repositorio productivo. [Fase 3](FASE_3.md) describe ese límite. Las 45 pruebas ejecutadas y 32 de dominio `UP-TO-DATE` del ejemplo y pulido pertenecen a la base **0.3.1 / código 8**; las 92 de 0.3.2 son otro resultado histórico. Las comprobaciones nuevas de 0.3.3 se registran aparte en [Validación](VALIDACION.md).

## Subir una función

Esta sección describe cómo subir otra función después de esta actualización. No se ejecuta por haber terminado, probado o preparado una versión. Crear el commit, subir la rama, integrar el pull request y publicar una Release son acciones distintas y requieren una instrucción humana precisa. La autorización actual cubre 0.4.0a, sin autorizar automáticamente el trabajo posterior.

Antes de crear el commit, actualiza [Changelog](../CHANGELOG.md) y [Fases](FASES.md) con el estado real del incremento. Revisa qué archivos incluye el cambio:

```powershell
git status
git diff
git add app/src/main CHANGELOG.md docs/FASES.md
git diff --cached
git commit -m "Añadir interfaz inicial"
git push -u origin HEAD
```

Si se solicita expresamente este paso, el ejemplo añade los archivos de la interfaz y su documentación. Si la función necesita otros archivos, inclúyelos de forma explícita y coordina las áreas compartidas. `HEAD` publica la rama actual, incluso si elegiste otro nombre. Crear un pull request, integrarlo y publicar la versión también quedan sujetos a la instrucción correspondiente; no se realizan automáticamente después de subir.

El trabajo sigue el ciclo de [Trabajo del equipo](TRABAJO_EQUIPO.md): una función por vez, prueba y revisión antes de escoger la siguiente.
