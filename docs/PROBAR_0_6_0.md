# Probar 0.6.0 en Android

**0.6.0 / código Android 28**. Descarga **citizensecurity-0.6.0-academico.apk** y su checksum desde [Release v0.6.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.0). Instálala encima de la versión anterior, con la misma firma académica, sin desinstalar ni borrar datos. Si Android informa un conflicto de firma, conserva los datos y revisa la instalación antes de desinstalar.

## Cuentas y datos propios

1. Abre **Crear cuenta**. El usuario admite de 3 a 30 letras sin acentos, números o guion bajo. Usa una contraseña de 15 a 128 caracteres y consérvala: la cuenta solo existe en este teléfono. El registro vuelve al login; no inicia sesión automáticamente.
2. Ingresa con esa cuenta. Inicio debe mostrar su usuario. Sus reportes, fotografías, borrador y solicitudes pendientes pertenecen a ese espacio.
3. Cierra sesión y vuelve a ingresar: sus datos deben conservarse. Una cuenta distinta empieza con su propio espacio; no debe ver los folios, fotos o borradores de otra.
4. **Desarrollador** y `admin/admin` abren el modo académico, donde permanecen los reportes históricos. No son permisos administrativos. Un folio del académico no aparece en una cuenta nueva.

Si Android cierra el proceso, una pantalla de cuenta pide volver al login. La app conserva sus archivos; no presenta su borrador como académico.

## Guardar y consultar un reporte

1. En **Inicio → Reportar**, llena tipo, prioridad, descripción, fecha y referencia; la fotografía es opcional.
2. En el mapa selecciona el punto, autoriza ubicación precisa y solicita GPS por el botón correspondiente. La confirmación exige una lectura reciente, precisa y no simulada dentro de 5 km del teléfono. Un GPS sin respuesta permite reintentar conservando el punto.
3. Revisa y pulsa **Guardar reporte**. Si aparece un aviso de posibles repetidos, revisa el folio anterior y decide si es otro incidente. El rango acordado sigue siendo mismo tipo, 200 m y hasta 2 horas antes o después del incidente.
4. Conserva el folio recibido y ábrelo en **Reportes guardados**. Comprueba campos y fotografía. Consultar un folio no requiere otra captura GPS.

Los reportes permanecen en el teléfono: no se envían a autoridades ni generan avisos de emergencia. El radio limita cercanía; no demuestra que ocurrió un incidente.

## Lista, búsqueda y recuperación

Busca texto de la descripción o referencia, fechas del incidente y filtros de tipo, estado o prioridad. **Buscar reportes**, **Limpiar búsqueda** y **Mostrar todos** vuelven al inicio, incluso si repites los mismos valores. Una fecha imposible o un intervalo invertido mantiene la búsqueda válida anterior.

La lista muestra hasta 50 coincidencias por página. **Más reportes**, **Anterior** y **Actualizar lista · volver al inicio** permiten recorrerla. Una búsqueda sin coincidencias puede indicar que aún quedan candidatos: usa **Continuar búsqueda** antes de interpretar que terminó. No hace falta crear muchos reportes únicamente para probar los botones; aparecen cuando existen suficientes datos.

Girar o abrir un detalle y volver conserva los criterios y la posición, con una lectura actual. Android puede recuperar esos metadatos tras cerrar el proceso para recuperar memoria; no se garantiza después de forzar cierre o quitar la tarea. No se restaura autorización GPS ni resultados completos.

## Mapa por zona

En **Inicio → Mapa de reportes**, mueve la cámara a la zona que quieres consultar. Puedes combinar fechas del incidente, tipo, estado y prioridad. Escribe dd/MM/aaaa o elige las fechas con el calendario; pulsa **Aplicar fechas** y luego **Consultar esta zona**.

Los pines y **Ver lista de esta zona** representan la misma muestra. Si está limitada, la pantalla lo avisa. Abrir un folio desde esa lista conserva el espacio de la cuenta. Mover la cámara o cambiar filtros invalida la muestra anterior y requiere otra consulta. La prioridad es la indicada por el autor. La búsqueda por texto pertenece a la lista general.

El mapa muestra calles y nombres con MapLibre/OpenFreeMap; su carga necesita conexión. La consulta de folios locales no depende de descargar el mapa.

## Historial real del folio

Abre un folio y pulsa **Consultar historial**. Un reporte nuevo muestra su evento de creación y la cuenta local cuando se conoce. **Actualizar historial** vuelve a consultar; **Eventos anteriores** y **Volver al inicio** aparecen cuando la página lo necesita. Si falla solo el historial, la ficha se conserva y puedes reintentar.

Un reporte de la versión anterior puede avisar que no hay un registro completo desde su creación: sus datos y estado se conservaron, pero no se inventaron eventos anteriores. La hora es la registrada por el teléfono y el orden sigue la secuencia de guardado.

El cambio administrativo de estado sigue deshabilitado: la asignación de permisos quedó pendiente por elección del usuario. No hay botón de administración ficticio ni privilegios por usar admin/admin. La fase 28 prepara su coordinación interna para continuar después.

## Para el compañero

Conserva tus cambios, ejecuta `git fetch origin`, `git switch main` y `git pull --ff-only origin main`. Sincroniza Gradle y elige **app** en Android Studio. verification:maps es el laboratorio separado; la APK del producto no lo instala. La interfaz del login y loading se conserva.

[Entrega y límites](ENTREGA_0_6_0.md) · [Historial](FASE_27.md) · [Base siguiente](FASE_28.md) · [Validación](VALIDACION.md) · [Avance 67.9%](AVANCE.md).
