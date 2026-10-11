# Fase 18 — aviso de posibles reportes repetidos · 0.5.5

Mejora visible de **4.2 Reportes** y **4.4 Consulta**, con código Android 25. El usuario confirmó **200 metros y 2 horas**. Se mantiene el mapa con calles, fotografía, copia del borrador y login/splash del compañero.

## Recorrido

Al pulsar **Guardar reporte en este teléfono** desde la revisión, la app busca incidentes del mismo tipo a 200 m o menos del punto y hasta 2 horas antes o después de la fecha del incidente. Abrir la revisión no inicia esa consulta ni el GPS. Una búsqueda completa sin coincidencias termina ese gesto de Guardar y pasa al guard existente.

Si aparecen coincidencias, el aviso muestra distancia aproximada, fecha, referencia y folio. Puedes abrir el reporte anterior y regresar con tu borrador íntegro. **Volver a revisar** cancela el guardado; **Es otro incidente, guardar** permite confirmar un incidente distinto. Compartir tipo, hora o zona no demuestra que se trate del mismo hecho ni que un reporte sea verdadero.

La consulta pertenece únicamente a este teléfono. El acceso académico no acredita autor ni permite consultar datos de otros teléfonos. Se muestran hasta cinco coincidencias entre como máximo 200 candidatos. Si hubo más candidatos o coincidencias, se informa que la búsqueda es limitada, incluso si la página quedó vacía. Un fallo pide decidir si reintentar o continuar; nunca equivale a una búsqueda sin coincidencias.

## Continuidad y protección

- `SimilarReportQuery` fija tipo, punto, hora, radio, ventana y UUID excluido. Los valores son válidos y acotados; los límites temporales incluyen nanosegundos y evitan desbordamiento. La caja esférica contempla polos y antimeridiano; la distancia decide el radio después de acotar candidatos en SQL.
- `SqliteReportRepository.findSimilar` usa argumentos SQL, orden estable y respuesta acotada. No escribe, captura GPS, cambia estados ni migra la base. Conserva SQLite esquema 2 y sus datos anteriores.
- `ReportSimilarityViewModel` vincula cada respuesta al `NewReport` completo y UUID preferido de la copia. Cancelar o cambiar ese contenido invalida respuestas y confirmaciones anteriores. La autorización se consume una vez y no se persiste ante muerte del proceso.
- Solo una búsqueda completa vacía puede terminar el gesto original de Guardar, con la revisión vigente y la Activity en `RESUMED`. Salir durante esa consulta la cancela; regresar no escribe. La rotación conserva el estado del modelo y un aviso visible puede abrir el folio sin perder el borrador.
- Coincidencias, búsqueda incompleta y fallo requieren un gesto adicional. Antes de insertar se mantienen las comprobaciones del checkpoint y el guard de GPS preciso, reciente, no simulado, precisión ≤100 m y distancia más precisión ≤5 km, junto a la integridad de la fotografía.
- Un intento pendiente se recupera o reintenta con su UUID durable. No pasa por esta advertencia ni genera otro folio. Si el borrador coincide con la copia, su folio se excluye de la búsqueda y `createOnce` recupera el resultado anterior.

## Comprobación

Pruebas de dominio cubren radio/tiempo inclusivos, coordenadas inválidas, extremos de fecha, antimeridiano/polos y límites inmutables. Las pruebas SQLite verifican filtrado real, nanosegundos, exclusión de folio, orden, truncamiento, reapertura y cancelación. Las del modelo comprueban generaciones, respuestas antiguas, confirmación y consumo único. Las pruebas de interfaz usan SQLite real aislado y una fuente GPS controlada exclusivamente en `androidTest`.

El cierre ejecutado se registra en [Validación](VALIDACION.md); no se acredita GPS físico por estas pruebas. [Probar la APK](PROBAR_0_5_5.md).

## Avance

**14/28 = 50%** con los criterios originales. No se agrega un hito por esta ampliación, por publicar una versión o por contar pruebas. Siguen pendientes cuentas reales/aislamiento, aceptación física final de GPS, administración, seguimiento y notificaciones.

Cierre: **831 casos distintos aprobados, 79 definiciones nuevas; debug/release compilan y lint sin errores**. [Validación](VALIDACION.md) conserva evidencia y límites.
