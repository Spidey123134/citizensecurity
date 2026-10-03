# Versiones y GitHub Releases

Cada versión del proyecto tendrá notas en español en GitHub Releases con los cambios, comprobaciones ejecutadas y límites de la entrega. La fuente de cambios es [Changelog](../CHANGELOG.md); el estado funcional se consulta en [Fases](FASES.md).

## Entrega oficial 0.2.1

| Identificador | Valor |
| --- | --- |
| Versión visible | `0.2.1` |
| Número de actualización Android | `6` |
| Etiqueta | `v0.2.1` |
| Publicación | [Release v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1) |
| Base | `main` actualizado, con splash y login visual del compañero |

**Desarrollador · funciones 0.2** y **admin / admin** permiten comprobar las funciones publicadas de fases 1 y 2, tanto en debug como en release. El login es una demostración local sin cuentas, sesiones ni privilegios reales. [Funciones publicadas](FUNCIONES_PUBLICADAS.md) describe los controles.

Se conservan las correcciones y el puente técnico de los PR #4 y #5. **0.3.0 y 0.4.0 siguen en Unreleased**, sin etiquetas ni releases; 0.4 continúa en su rama separada. Las herramientas oficiales no ofrecen guardado ni consulta de esas fases.

Se aprobaron 14 JVM de app, 9 instrumentadas SQLite en API 37, lint debug/release y ambas compilaciones. Las 32 de dominio conservaron su resultado aprobado mediante `UP-TO-DATE`; quedan 13 advertencias visuales previas. El APK adjunto se identifica como **debug**, firmado para pruebas, con SHA-256 en [Validación](VALIDACION.md). El APK release sin firma se compiló como comprobación y no se distribuye.

`0.3.1` fue un identificador provisional sin publicar. El código Android se elevó a 6 para actualizar compilaciones locales recientes sin borrar datos. `v0.2.0` conserva su commit original.

## Desarrollo 0.4.0 — consulta local, Unreleased

El incremento autorizado de consulta se conserva desde `474d07c` y continúa en una rama separada, actualizada con la base oficial `0.2.1`. Usa `versionName = "0.4.0"` y `versionCode = 7`, superior al código 6 de la entrega oficial. No tiene etiqueta, release ni APK publicado; `v0.2.1` sigue siendo la entrega oficial y fase 3 / `0.3.0` permanece en `Unreleased`.

**Añadido:** `ReportQueryViewModel`, fábrica compartida y propiedad `MainActivity.reportQueryViewModel`; consulta UUID con estados de encontrado, inexistente, formato inválido o fallo de lectura. La tarea `:core:data:consultarReporte` comprueba la reapertura de dos reportes, su estado inicial, el orden y la ausencia de cambios al consultar, y exporta `consulta.json` en una base temporal. El [plan del proyecto](PLAN_DEL_PROYECTO.md) relaciona la [fase 4](FASE_4.md) con el apartado 4.4 del PDF y conserva sus requisitos pendientes.

**Corregido:** los modelos de guardado y consulta comprueban la cancelación antes de publicar resultados o errores tardíos del repositorio. Las dos regresiones JVM fallaron antes de corregir y aprobaron después.

**Comprobado:** 24 JVM de app (8 de guardado, 8 de consulta y 8 de herramientas), 32 unitarias de dominio ejecutadas de nuevo y 10 instrumentadas SQLite en API 37 aprobadas, sin fallos ni errores. Los JSON de consulta y guardado fueron correctos; lint debug/release terminó sin errores y se compilaron el APK debug y el APK release sin firma. Datos sin incidencias y 13 advertencias visuales previas. [Validación](VALIDACION.md) conserva los comandos y artefactos reales de la comprobación.

La rama no añade una pantalla de consulta y conserva el esquema SQLite. El botón de desarrollador y `admin / admin` siguen limitados a funciones oficiales de fases 1 y 2. Las cuentas, el aislamiento de reportes propios y el seguimiento administrativo siguen pendientes. No se instaló el APK 0.4 sobre la aplicación oficial del usuario; flujo visual, rotación real, integración en `main`, publicación y aceptación funcional permanecen pendientes.

## Historial del identificador provisional 0.3.1

Durante la auditoría se usó `versionName = "0.3.1"` y `versionCode = 4`, sin etiqueta ni release. Se conservan los resultados de esas pruebas como historial. El siguiente mantenimiento es `0.2.1`; este identificador no señala una próxima entrega.

Se reprodujo un fallo heredado de `0.1.0`: un guardado cancelado mientras esperaba el monitor podía insertar al conseguir el turno. La corrección comprueba la cancelación dentro del monitor, antes de ejecutar SQLite. La regresión falló antes de corregir y la suite posterior de 9 pruebas instrumentadas aprobó en API 37. Las 32 unitarias mantuvieron su resultado anterior mediante `UP-TO-DATE`.

Un segundo candidato, texto Unicode interior, conservó exactamente los diez casos comprobados en Android. Se mantiene su prueba como cobertura y el validador conserva sus reglas. El parche conserva el esquema y la función de reportes.

La base se sincronizó con `7e90109`, que añadió splash y login visual del compañero, sin autenticación implementada. Se restauró la aplicación compartida y la exclusión de respaldos en el manifiesto. En aquella comprobación, compilación y lint de `app` se bloquearon por `activity_splash.xml` ausente. El compañero añadió después ese recurso en `e18e636`, ya incorporado para el mantenimiento `0.2.1`. [Validación](VALIDACION.md) conserva la diferencia entre ambos estados.

## 0.3.0 — fase 3 adelantada, Unreleased

Trabajo adelantado, con código existente y pruebas locales del 2 de octubre de 2026. La entrega de fase 3 sigue pendiente; `0.3.0` permanece en `Unreleased`, sin etiqueta ni release. Su APK de desarrollo usó `versionName = "0.3.0"` y `versionCode = 3`; la entrega oficial posterior es `0.2.1`, código 6, y la rama de consulta usa `0.4.0`, código 7. Las pruebas técnicas no sustituyen la entrega ni la aceptación funcional de la fase.

**Añadido:** la tarea `:core:data:guardarReporte` reutiliza la instrumentación Android para mostrar un reporte guardado con folio, los datos recibidos y las coordenadas recuperadas después de reabrir. También comprueba el rechazo de un borrador inválido sin añadir registros. Exporta `reporte.json` y `rechazo.json` por dispositivo, usando bases temporales independientes que se eliminan al terminar.

**Corregido:** el validador rechaza texto Unicode que no puede conservarse al guardar, con errores por campo; el repositorio normaliza los ceros de coordenadas para que el reporte devuelto y el recuperado coincidan. Las regresiones reprodujeron los fallos antes de corregirlos y aprobaron después.

**Comprobado:** suites unitarias e instrumentadas en API 37 aprobadas, ejemplos ejecutados, evidencia JSON exportada, lint sin errores y APK debug `0.3.0` construido. `app` conserva sus 2 advertencias existentes. Los resultados completos se mantienen en [Validación](VALIDACION.md) y [Changelog](../CHANGELOG.md); la demostración de guardado y sus JSON se describen en [Fase 3](FASE_3.md).

Este incremento mantiene el esquema SQLite y los campos del contrato del reporte. No añade interfaces, login ni SDK de Google Maps. Las fechas fijas del reporte pertenecen al caso de prueba; `generatedAt` de los JSON contiene la hora real de generación. La verificación en teléfono físico y API 26 sigue pendiente. No se publica un APK adjunto ni una nueva release: el trabajo se conserva como `Unreleased`.

## Entrega 0.2.0

| Identificador | Valor de la entrega |
| --- | --- |
| Versión visible (`versionName`) | `0.2.0` |
| Número de actualización Android (`versionCode`) | `2` |
| Etiqueta Git | `v0.2.0` |
| Página de publicación | [Release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) |
| Estado | Publicada el 2 de octubre de 2026, hora de México |
| Commit etiquetado | `aa29990847f10e80421fca15efe59a09b25838be`, integrado mediante [PR #2](https://github.com/Spidey123134/citizensecurity/pull/2) |

La versión prepara la revisión de la validación del reporte y del par de coordenadas usando el contrato existente. Añade ejemplos de consola para un reporte personalizado y para tres casos de ubicación: válida, par incompleto y fuera de rango. También documenta la futura conexión de los datos con Google Maps y el reparto del equipo.

El mapa, el pin, la confirmación de ubicación y el login siguen pendientes del compañero. El SDK, proyecto de Google, clave y facturación todavía no están conectados. Para esta versión se ejecutaron los ejemplos y 28 pruebas unitarias del dominio, además de las 5 pruebas instrumentadas de SQLite en API 37. Lint terminó sin errores y el APK debug se construyó y su firma se comprobó. El detalle y las dos advertencias existentes están en [Validación](VALIDACION.md).

La entrega `v0.2.0` incluye código fuente y notas; no se adjuntará un APK en este incremento. El APK debug se construyó y verificó localmente. Los ejemplos, las 28 pruebas unitarias, las 5 instrumentadas y lint aprobaron; `app` conserva sus 2 advertencias conocidas. La evidencia completa está en [Validar reporte](VALIDAR_REPORTE.md).

La aplicación todavía tiene un contenedor visual vacío; los ejemplos de consola se ejecutan con Gradle y no forman parte del APK. En una entrega posterior que incluya APK, el nombre del adjunto debe identificar su versión y tipo de compilación, acompañado de su SHA-256.

## Preparar las siguientes versiones

1. Elige un incremento pequeño y comprobable. Actualiza el [Changelog](../CHANGELOG.md) y [Fases](FASES.md) con el comportamiento y estado reales.
2. Incrementa `versionCode` en `app/build.gradle.kts` respecto de la última versión publicada. Actualiza `versionName` con el formato `mayor.menor.parche`: una corrección compatible aumenta el parche; una función compatible aumenta la menor; un cambio incompatible aumenta la mayor. Usa la misma versión en el título, la etiqueta y el nombre del APK.
3. Ejecuta las comprobaciones apropiadas al incremento y construye el artefacto. Registra resultados y advertencias; si una comprobación no se ejecutó, indica el motivo y su límite.
4. Integra la rama revisada y crea la etiqueta sobre el commit que contiene esos cambios y la versión de la aplicación. Conserva las etiquetas publicadas: una corrección posterior usa otra versión en lugar de mover una etiqueta existente.
5. Publica GitHub Release con las notas en español. Adjunta el APK de esa versión si forma parte de la entrega, indicando si es debug o release y su SHA-256.
6. Comprueba que la etiqueta corresponde al commit integrado, que la release está publicada y que los adjuntos corresponden al artefacto verificado. Registra el enlace y la evidencia de publicación en el changelog y las fases.

Para comprobar el avance `0.4.0 — Unreleased` en su rama:

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:consultarReporte :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
```

Las tareas `consultarReporte` y `guardarReporte` requieren un emulador o teléfono y comparten la dependencia `connectedDebugAndroidTest`; en la misma ejecución no hace falta repetir esa instrumentación en un comando separado. Exportan evidencia fuera de Git y no consultan la base productiva del usuario. Los ejemplos de validación muestran resultados y pueden terminar normalmente aun cuando una entrada sea inválida; comprueba los mensajes y registra los casos usados. Las pruebas automáticas y los informes de lint proporcionan las comprobaciones de regresión.

Las claves, contraseñas, configuración personal, bases de datos y respaldos permanecen fuera del repositorio y de los adjuntos. Las notas y los mensajes de publicación describen el producto, sus cambios y cómo revisarlos, sin nombres de asistentes ni datos personales.

## Contenido de las notas

Las notas de cada versión deben incluir:

- **Cambios:** comportamiento añadido o corregido, con el resultado observable.
- **Comprobaciones:** comandos realmente ejecutados, cantidades de pruebas y resultados; dispositivo o emulador cuando corresponda.
- **Cómo revisarlo:** enlace a la guía y comandos del incremento.
- **Límites:** funciones que siguen pendientes, advertencias relevantes y verificaciones no ejecutadas.
- **Artefactos:** nombre del APK, tipo de compilación y SHA-256, si se adjunta.

Publicar una versión hace disponible una entrega concreta. La revisión técnica y la aceptación funcional del usuario se registran por separado.

La publicación se realiza siguiendo [la guía oficial de GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
