# Trabajo del equipo

## Alcance vigente — base 0.1.0 revisión 2 beta, 13 de noviembre de 2026

El usuario pidió expresamente reorganizar el CÓDIGO de la primera versión con la estructura del pizarrón y publicar una beta con el documento y diagrama preparados. Esta instrucción sustituye para esta copia las restricciones de estructura de los puntos inferiores: un módulo app, paquetes navigation/screen/data, mismos modelos/reglas/persistencia de b4f80a8. Se permite el traslado de código/pruebas, renombrar el gestor SQLite a AdminReportes y separar los contenedores visuales en Home/ReportDetails/Navigation, conservando la base visual vacía en XML. No añadir formularios, cuentas, edición/borrado de reportes, mapas, GPS u otras funciones de la línea avanzada.

Rama beta/0.1.0-rev2 en worktree propio, sin modificar el checkout avanzado ni sus cambios locales. Versión 0.1.0-rev.2-beta/código2; paquete separado com.example.citizensecurity.basebeta para coexistir con la app actual. La publicación autorizada es tag v0.1.0-rev.2-beta y GitHub prerelease con latest=false, fuentes, APK y checksum; nunca integrar esta rama antigua en main ni sustituir la Release0.7.0. El usuario completó la autenticación de GitHub como Spidey123134; guardada en keyring, sin secretos en Git.

Validar reglas/pruebas trasladadas, lint y compilación, instrumentación SQLite y navegación en un emulador propio con serial explícito. No teléfono, borrado de datos del usuario o cambios de PIN. Registrar solo resultados ejecutados y verificar descarga de la APK publicada. Incluir docs/ESTRUCTURA_INICIAL.md (presentación histórica) y docs/ESTRUCTURA_BETA.md (código reorganizado real) en las notas. Los puntos inferiores conservan el estado inicial de octubre.

Cierre técnico beta: 28 JVM y siete Android (cinco SQLite, dos navegación) ejecutados y aprobados, sin fallos/errores/omitidos. Siete traslados equivalentes salvo paquetes/imports/nombre del gestor; sin cambio de reglas o esquema. Build/lint debug y release correctos, lint cero errores/siete avisos por variante. APK 1,502,794 bytes, SHA-256 4263c3bfa9c312083ba2503c5749195493b6f00df4b3164cfe80f39b434fa508, misma firma académica comprobada. AVD propio API26 cerrado conservando carpeta; sin teléfono.

Publicada y verificada: Release 444734423, 13 de noviembre de 2026 a las 21:01:51 UTC, prerelease v0.1.0-rev.2-beta sobre ef6c60177a3aebd801331b61ea2a00e5410cdf45. APK, ZIP de fuentes (92,147 bytes; SHA e761ab0512b1586ccdd73eb7dcf00784db74671feab435eb1ff18a4ce039334f) y checksum descargados de nuevo: hash/bytes/digest de GitHub coinciden. Recibo .gradle/validacion/beta010/published-download-receipt.json. La oficial v0.7.0 sigue latest, assets anteriores idénticos y main intacto en cebc0d46cf8d30ff210c8e5e2a05cb8141aa5d37. No mover tag ni sustituir assets; cierre documental solo en esta rama beta.

Lee README.md y docs/TRABAJO_EQUIPO.md antes de editar.

- Proyecto Android académico en Kotlin para reportes de incidentes. Mantén el nombre CITIZENSECURITY y el paquete com.example.citizensecurity.
- El compañero desarrolla las interfaces en app/src/main: pantallas, layouts, componentes visuales y navegación. La actividad inicial es únicamente un contenedor vacío para esa tarea.
- Nuestra primera función es validar y guardar un reporte local, con consulta para comprobar que persistió. Se implementa en core/domain y core/data, sin crear formularios, menús, login, simulador, GPS, fotos ni servidor.
- Trabaja una función por vez. Presenta sus resultados y pruebas al usuario antes de pasar a otra función. No amplíes el alcance por iniciativa propia.
- El proyecto anterior se conserva fuera de este repositorio como referencia. No copies todas sus pantallas o funciones ni borres su código, documentos o respaldo.
- Usa versiones estables comprobadas y conserva un wrapper con checksum. Java para ejecutar Gradle y bytecode Android son configuraciones distintas.
- Los mensajes del producto y ejemplos deben usar lenguaje natural, sin nombres de asistentes, etiquetas internas de prueba ni datos personales.
- No guardes contraseñas, claves, archivos de cuentas de servicio, local.properties, backups o documentos personales en Git.
- Las migraciones de SQLite deben ser explícitas y preservar datos. No uses recreación destructiva ni conviertas guardado local en envío a autoridades.
- Valida las reglas con pruebas unitarias y la persistencia con instrumentación en un dispositivo o emulador. Ejecuta lint y assembleDebug. Documenta en español qué se ejecutó y sus límites.
- La configuración del proyecto y los contratos compartidos se coordinan con el equipo. El desarrollo posterior usa ramas de tarea y revisión de cambios.
