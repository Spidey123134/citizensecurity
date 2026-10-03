# Trabajo del equipo

Lee README.md y docs/TRABAJO_EQUIPO.md antes de editar.

- Proyecto Android académico en Kotlin para reportes de incidentes. Mantén el nombre CITIZENSECURITY y el paquete com.example.citizensecurity.
- El compañero desarrolla las interfaces y el login: pantallas, layouts, componentes visuales y navegación. Ya añadió SplashActivity, activity_splash.xml y el layout visual de login; la autenticación sigue pendiente de su parte. Conserva sus cambios y no implementes su login por iniciativa propia.
- Nuestra primera función es validar y guardar un reporte local, con consulta para comprobar que persistió. Se implementa en core/domain y core/data, sin crear formularios, menús, login, simulador, GPS, fotos ni servidor.
- La línea nueva acordada es ubicar el incidente en Google Maps. Nuestra parte recibe, valida y conserva las coordenadas; el compañero desarrolla la vista del mapa, el marcador y sus controles. El incremento 0.2.0 demuestra la validación de puntos sin añadir todavía el SDK o configurar un servicio externo.
- Trabaja una función por vez. Presenta sus resultados y pruebas al usuario antes de pasar a otra función. No amplíes el alcance por iniciativa propia.
- El proyecto anterior se conserva fuera de este repositorio como referencia. No copies todas sus pantallas o funciones ni borres su código, documentos o respaldo.
- Usa versiones estables comprobadas y conserva un wrapper con checksum. Java para ejecutar Gradle y bytecode Android son configuraciones distintas.
- Los mensajes del producto y ejemplos deben usar lenguaje natural, sin nombres de asistentes, etiquetas internas de prueba ni datos personales.
- No guardes contraseñas, claves, archivos de cuentas de servicio, local.properties, backups o documentos personales en Git.
- Las migraciones de SQLite deben ser explícitas y preservar datos. No uses recreación destructiva ni conviertas guardado local en envío a autoridades.
- Valida las reglas con pruebas unitarias y la persistencia con instrumentación en un dispositivo o emulador. Ejecuta lint y assembleDebug. Documenta en español qué se ejecutó y sus límites.
- La configuración del proyecto y los contratos compartidos se coordinan con el equipo. El desarrollo posterior usa ramas de tarea y revisión de cambios.
- El usuario solicitó acoplar la interfaz con la función de reportes. La composición compartida usa `ReportViewModel` en `app`, la fábrica de `CitizenSecurityApplication` y `MainActivity.reportViewModel`. Conserva los layouts y el login del compañero; conecta su futuro formulario mediante `NewReport` y los estados del modelo, siguiendo docs/ACOPLAR_INTERFAZ.md.
- Mantén CHANGELOG.md y docs/FASES.md al entregar un incremento. Distingue código implementado, pruebas ejecutadas y revisión del usuario; no marques una fase revisada sin esa revisión.
- Entrega los incrementos acordados por versión mediante GitHub Releases, con etiqueta, cambios concretos y pruebas reales. Sigue docs/RELEASES.md; conserva las etiquetas publicadas y no presentes la publicación como aceptación funcional del usuario.
- El usuario pidió mantener la fase 3 y la próxima versión 0.3.0 en Unreleased. Documenta y comparte sus cambios sin crear una etiqueta o publicar esa release hasta que el usuario cambie esta indicación.
- La entrega oficial de mantenimiento es 0.2.1, código Android 6, sobre main actualizado. 0.3.1 fue un identificador provisional sin publicar. Conserva el código adelantado de fase 3, pero registra la entrega 0.3.0 como Unreleased hasta que el usuario llegue a esa fase. Preparar o integrar otros avances no equivale a publicar una release.

- El usuario autorizó expresamente el botón de desarrollador y el acceso local público `admin / admin` en la versión oficial 0.2.1 de main. Las herramientas deben ofrecer exclusivamente recepción, validación y pruebas de coordenadas de fases 1 y 2, en debug y release. No exponen guardado ni consulta de fases Unreleased. La demostración no crea cuentas, sesiones ni privilegios reales; las credenciales de servicios o cuentas reales siguen fuera de Git.
- Después de comprobar la base oficial, el usuario autorizó retomar 0.4.0 en una rama separada, con código Android 7. Conserva su avance anterior y mantén 0.3.0 y 0.4.0 en Unreleased. El incremento es consultar por folio y leer el estado local mediante el repositorio compartido y ReportQueryViewModel, siguiendo docs/PLAN_DEL_PROYECTO.md y docs/FASE_4.md. El plan se basa en el PDF; cuentas, aislamiento por usuario y seguimiento administrativo siguen pendientes. No añadas interfaces de consulta ni mezcles estos avances con la release oficial de la línea 0.2.
