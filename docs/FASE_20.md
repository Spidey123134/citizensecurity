# Fase 20 — encontrar reportes locales · 0.5.7

Esta entrega continúa **4.4 Consulta** por la petición de avanzar la app. Código Android 27. Hace útil la lista cuando ya hay varios folios guardados, manteniendo login/loading del compañero y las funciones anteriores.

## Recorrido

En **Desarrollador → Inicio → Reportes guardados** hay búsqueda en **Descripción o referencia** y límites **Desde** / **Hasta**. Escribe las fechas como dd/MM/aaaa o pulsa **Elegir** para usar el calendario del sistema. Cada fecha es opcional; dejar ambas vacías no limita el período.

**Buscar reportes** aplica el texto y las fechas escritos. Una fecha imposible, un formato incompleto o Desde posterior a Hasta muestran un error y conservan la búsqueda válida anterior. Los filtros de tipo y estado se combinan con la búsqueda aplicada. **Limpiar búsqueda** y **Mostrar todos** retiran texto, fechas, tipo y estado. El contador distingue la cantidad visible de todos los reportes leídos en este teléfono.

Girar o abrir un detalle y volver conserva lo escrito, los filtros aplicados y la zona de interpretación de las fechas. Editar sin aplicar no sustituye los filtros vigentes. Abrir un calendario tampoco inicia una búsqueda ni obtiene ubicación. El detalle continúa ofreciendo fotografía, copia del folio y acceso al punto guardado.

## Contrato de lectura

ReportSearchFilters valida fechas estrictas y un intervalo ordenado antes de aplicar. Usa **occurredAt**, la fecha del incidente, en lugar de **createdAt**, la fecha de guardado. Desde empieza al inicio de su día local; Hasta termina antes del inicio del día siguiente. Ambos días elegidos quedan incluidos, respetando días más cortos/largos por cambios de horario, sin sumar 24 horas fijas. La zona queda conservada al recrear la pantalla.

El texto se normaliza por mayúsculas, acentos y espacios. La frase normalizada se busca de forma literal en descripción o referencia; no modifica esos campos ni interpreta instrucciones SQL. Se combina con tipo/estado y mantiene el orden de la lectura original. La búsqueda opera sobre la lista leída, sin guardar, solicitar GPS ni cambiar un reporte.

Los filtros nuevos son exclusivamente de la lista. La consulta por zona del mapa conserva sus filtros anteriores; no se acredita paridad entre mapa/lista para texto y fecha ni una consulta de otros teléfonos.

## Corrección de folios

Antes, volver de un detalle mientras su folio seguía leyéndose dejaba los accesos de consulta bloqueados hasta terminar esa lectura. ReportQueryViewModel ahora expone **cancelQuery()**: volver cancela e invalida la solicitud y libera la consulta. Una nueva solicitud, incluida una entrada inválida, sustituye la anterior; los resultados y errores tardíos no reemplazan el folio nuevo.

El Job se reserva antes de publicar Loading y cada solicitud tiene su generación, para conservar identidad incluso ante reentrancia. Cerrar el modelo cancela sus lecturas; volver no cierra el repositorio compartido. La cancelación al volver no se conecta a una rotación de pantalla. Se mantienen errores comprensibles, sin detalles privados de SQLite.

## Conservación y avance

Login/loading, acceso académico, SQLite esquema 2, reportes y fotos anteriores, diarios/UUID, CAS, createOnce, aviso de posibles repetidos y comprobación GPS de 5 km conservan sus contratos. El acceso académico no identifica autores ni confiere permisos administrativos. Los reportes siguen locales, sin envío a autoridades ni avisos de emergencia.

**15/28 = 53.6%**, mismos 28 hitos/pesos. La fase fortalece los dos hitos existentes de consulta; no completa reportes por usuario ni seguimiento de estados y no suma otro criterio de mapas. La aceptación manual GPS ya registrada en 0.5.6 permanece separada de pruebas automatizadas. Cuentas reales/aislamiento por autor, seguimiento administrativo y notificaciones siguen pendientes.

## Comprobación

**545 casos de app ejecutados y aprobados; 38 nuevos.** Debug/release compilan y lint no tiene errores (22 advertencias por variante). Se reutilizan por separado 349 casos anteriores de dominio/datos sin cambios; detalle en Validación.

[Pasos para Android](PROBAR_0_5_7.md) · [Avance](AVANCE.md) · [Validación](VALIDACION.md).

La búsqueda admite hasta 512 caracteres, las fechas 32 y el campo de folio 64. Un pegado mayor muestra el error y conserva el texto anterior; no recorta ni altera reportes guardados. El aviso de base vacía aparece junto al encabezado.
