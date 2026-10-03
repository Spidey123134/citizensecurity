# Versiones y GitHub Releases

Cada versión del proyecto tendrá notas en español en GitHub Releases con los cambios, comprobaciones ejecutadas y límites de la entrega. La fuente de cambios es [Changelog](../CHANGELOG.md); el estado funcional se consulta en [Fases](FASES.md).

## Entrega 0.2.0

| Identificador | Valor de la entrega |
| --- | --- |
| Versión visible (`versionName`) | `0.2.0` |
| Número de actualización Android (`versionCode`) | `2` |
| Etiqueta Git | `v0.2.0` |
| Página de publicación | [Release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) |

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

Para la base actual, las comprobaciones son:

```powershell
.\gradlew.bat :core:domain:validarReporte :core:domain:validarUbicaciones --console=plain
.\gradlew.bat :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
.\gradlew.bat :core:data:connectedDebugAndroidTest --console=plain
```

La instrumentación requiere un emulador o teléfono. Los ejemplos muestran resultados y pueden terminar normalmente aun cuando una entrada sea inválida; comprueba los mensajes y registra los casos usados. Las pruebas automáticas y los informes de lint proporcionan las comprobaciones de regresión.

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
