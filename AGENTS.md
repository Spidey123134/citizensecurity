# Trabajo del equipo

Lee README.md y docs/TRABAJO_EQUIPO.md antes de editar.

- Proyecto Android académico en Kotlin para reportes de incidentes. Mantén el nombre CITIZENSECURITY y el paquete com.example.citizensecurity.
- El compañero desarrolla las interfaces y el login: pantallas, layouts, componentes visuales y navegación. La actividad inicial es únicamente un contenedor vacío para esa tarea. No implementes su login por iniciativa propia.
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
- Mantén CHANGELOG.md y docs/FASES.md al entregar un incremento. Distingue código implementado, pruebas ejecutadas y revisión del usuario; no marques una fase revisada sin esa revisión.
- Entrega los incrementos acordados por versión mediante GitHub Releases, con etiqueta, cambios concretos y pruebas reales. Sigue docs/RELEASES.md; conserva las etiquetas publicadas y no presentes la publicación como aceptación funcional del usuario.
- El usuario pidió mantener la fase 3 y la próxima versión 0.3.0 en Unreleased. Documenta y comparte sus cambios sin crear una etiqueta o publicar esa release hasta que el usuario cambie esta indicación.
