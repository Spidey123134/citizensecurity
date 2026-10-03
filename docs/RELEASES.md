# Versiones y GitHub Releases

Cada versión del proyecto tendrá notas en español en GitHub Releases con los cambios, comprobaciones ejecutadas y límites de la entrega. La fuente de cambios es [Changelog](../CHANGELOG.md); el estado funcional se consulta en [Fases](FASES.md).

## 0.3.1 — Unreleased

Parche de desarrollo con `versionName = "0.3.1"` y `versionCode = 4`, sin etiqueta `v0.3.1` ni release publicada. Incluye el trabajo previo de `0.3.0`, que también permanece sin publicar. La última release continúa siendo `v0.2.0`.

Se reprodujo un fallo heredado de `0.1.0`: un guardado cancelado mientras esperaba el monitor podía insertar al conseguir el turno. La corrección comprueba la cancelación dentro del monitor, antes de ejecutar SQLite. La regresión falló antes de corregir y la suite posterior de 9 pruebas instrumentadas aprobó en API 37. Las 32 unitarias mantuvieron su resultado anterior mediante `UP-TO-DATE`.

Un segundo candidato, texto Unicode interior, conservó exactamente los diez casos comprobados en Android. Se mantiene su prueba como cobertura y el validador conserva sus reglas. El parche conserva el esquema y la función de reportes.

La base se sincronizó con `7e90109`, que añadió splash y login visual del compañero, sin autenticación implementada. Se restauró la aplicación compartida y la exclusión de respaldos en el manifiesto, conservando sus cambios visuales. El estado integrado no genera APK: falta `activity_splash.xml`; la compilación y lint de `app` se bloquean por ese recurso. La revisión se prepara como PR borrador y no se integra en `main` hasta resolverlo. [Validación](VALIDACION.md) distingue las pruebas de datos aprobadas antes del commit visual del estado actual de la aplicación. No se publica etiqueta, release ni APK del parche.

## 0.3.0 — base previa también Unreleased

Incremento implementado y probado localmente el 2 de octubre de 2026, sin etiqueta `v0.3.0` y sin release publicada. Su APK de desarrollo usó `versionName = "0.3.0"` y `versionCode = 3`; el desarrollo actual es `0.3.1`. La release y la etiqueta publicadas de `v0.2.0` se conservan. La aceptación funcional sigue pendiente.

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

Para el incremento actual, la comprobación completa es:

```powershell
.\gradlew.bat :core:domain:test :core:domain:validarReporte :core:domain:validarUbicaciones :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

La tarea `guardarReporte` requiere un emulador o teléfono y depende de `connectedDebugAndroidTest`; no hace falta ejecutar de nuevo esa instrumentación en un comando separado. Los ejemplos de validación muestran resultados y pueden terminar normalmente aun cuando una entrada sea inválida; comprueba los mensajes y registra los casos usados. Las pruebas automáticas y los informes de lint proporcionan las comprobaciones de regresión.

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
