# Fase 44 — resultado del incidente y quién lo declaró

**17 de octubre de 2026 · desarrollo local Unreleased, validada.** El usuario pidió distinguir incidentes solucionados de los que siguen sin solucionarse y eligió que puedan declararlo **ambos: autor y administrador, identificando quién lo marcó**. Es la ampliación **E12** de [Mejoras útiles](MEJORAS_UTILES.md), vinculada al seguimiento de 4.4 y la administración de 4.6. El PDF conserva **28/28 = 100% de construcción funcional local**; esta mejora no añade criterios ni acredita aceptación física o recepción institucional.

## Resultado separado del seguimiento

| Resultado | Significado local |
|---|---|
| **Sin confirmar** | No hay una declaración vigente de que se solucionó o sigue sin solucionarse. Es el valor inicial, sin inventar una declaración. |
| **Solucionado** | El actor identificado declaró que el problema se solucionó, con fecha y motivo. |
| **Sigue sin solucionarse** | El actor identificado declaró que el problema sigue pendiente, con fecha y motivo. |

El resultado es independiente de **Reportado → En revisión → Atendido → Cerrado**. Cerrar el seguimiento no convierte un incidente en solucionado; declarar un resultado tampoco cierra ni reabre su estado administrativo. Un reporte antiguo, incluso Cerrado, comienza **Sin confirmar** si no tiene declaración de resultado. No se infiere una solución a partir del tiempo transcurrido, del GPS, del estado o del rol.

El resultado actual resume la última declaración guardada. El historial conserva las anteriores: quién lo declaró por UUID de cuenta, si actuó como **Autor** o **Administrador local**, fecha, motivo, resultado anterior/nuevo, secuencia y UUID de operación. Una corrección no sobrescribe ni elimina declaraciones. Volver a **Sin confirmar** también es una declaración auditada; no borra el historial ni significa que jamás hubo una opinión previa.

## Quién puede declarar

El autor se reconoce mediante el UUID real del evento de creación del reporte, no por el nombre escrito, el usuario visible, el dueño supuesto del archivo o un valor enviado por la interfaz. Una cuenta real puede declarar como autor únicamente donde ese evento lo identifica. En reportes antiguos con autor desconocido no se asigna uno por suposición.

El administrador necesita asignación local vigente y la capacidad explícita **DECLARE_RESOLUTION**. Se comprueba independientemente de **READ_REPORTS** y **CHANGE_STATUS**, y su escritura registra **Administrador local**. `admin/admin` conserva acceso académico y no obtiene permiso de declaración. La política se comprueba de nuevo antes de confirmar la transacción; una pantalla abierta no conserva un permiso revocado.

Ambos recorridos operan en el **mismo espacio local capturado de esa cuenta e instalación**. Esta fase no permite que el administrador consulte teléfonos ajenos, cruce espacios de otras cuentas, suplante a un autor o reciba reportes de una institución. Cuando una cuenta es autora y además tiene asignación, la declaración se identifica como **Autor**; si el evento de creación no la identifica, necesita la capacidad administrativa vigente y actúa como **Administrador local**. El repositorio resuelve esa autoridad; la interfaz no elige ni falsifica el rol.

## Recorrido de uso

Desde el detalle guardado o la consulta administrativa se muestra **Resultado del incidente** y su historial. Cargar esta sección es lectura: no declara un resultado, modifica estados, captura GPS, publica avisos, envía mensajes ni reconoce otra operación.

1. Elegir un resultado diferente del actual y escribir un motivo válido de **5–500 caracteres**.
2. Pulsar **Revisar** para comprobar resultado, motivo y actor. Revisar o girar el teléfono no escribe.
3. Pulsar **Confirmar** mediante otro gesto. Antes de escribir se releen la revisión del reporte, sesión y autoridad; un cambio concurrente exige revisar otra vez.
4. Consultar el recibo y el historial para comprobar actor, fecha y motivo. Una corrección necesita una nueva revisión y confirmación y conserva los eventos previos.

Una operación en curso conserva su UUID y cuerpo exactos **en el modelo vivo en memoria**, también durante un giro. Ante un resultado incierto, **Comprobar recibo · solo lectura** busca únicamente el recibo: no reintenta por su cuenta ni inventa una segunda declaración. **Reintentar la misma declaración** exige otro gesto y conserva su identidad. Este corte no incorpora un diario durable de declaraciones pendientes: tras cerrar el proceso se vuelve a consultar lo realmente guardado, sin restaurar o repetir una escritura. El diario de cambios administrativos conserva su recorrido separado. Un recibo coincidente prueba esa declaración local; no la convierte en confirmación por una autoridad. Cambiar de cuenta retira los datos de la sección y no entrega una respuesta anterior a la sesión nueva.

## Persistencia y migración

SQLite incorpora el esquema **5** mediante una migración aditiva sobre el esquema 4. Conserva reportes, fotos, contactos e historiales previos; no reconstruye la base ni inventa resultados para reportes existentes. Resultado actual e historial se leen en un mismo snapshot coherente; páginas acotadas mantienen orden y continuidad. La secuencia esperada evita que dos revisiones de un mismo resultado se confirmen como si ninguna hubiese cambiado.

La operación guarda resultado, evento y recibo en una transacción. Repetir el mismo UUID/cuerpo devuelve el mismo recibo; reutilizar el UUID con otro cuerpo o confirmar una revisión vencida se rechaza. Los errores no se convierten en un historial vacío o una solución por defecto. La declaración es un registro académico local: no verifica por sí misma la realidad del incidente ni una intervención policial.

## Criterios y validación

- Autor real y administrador autorizado pueden declarar; UUID, rol, fecha y motivo quedan visibles y persistidos.
- Académico y cuentas sin autoridad no obtienen escritura; un autor desconocido no se atribuye por suposición. Sesión o rol revocados bloquean antes del commit.
- Sin confirmar es el estado inicial sin evento inventado; Cerrado sigue siendo independiente.
- Revisar, girar, navegar o recuperar un recibo no agregan declaraciones; el reintento explícito mantiene el mismo UUID.
- Correcciones e intervenciones de distintos actores conservan el historial; revisión concurrente y operación reutilizada se rechazan.
- Migración aditiva preserva datos de esquema 4, con lecturas coherentes, límites y errores conservadores.

**Cierre: 1662 casos distintos aprobados / 108 nuevos**, cero fallos, errores u omitidos finales. No se suman los cortes de fases anteriores ni los intentos de un mismo caso. Todo el corte se ejecutó; el desglose es:

| Capa | Distintos ejecutados | Nuevos en fase 44 |
|---|---:|---:|
| Dominio JVM | 216 | 11 |
| Datos JVM | 158 | 10 |
| App JVM | 1077 | 51 |
| Datos Android API 37 | 180 | 28 |
| App Android API 37 | 31 | 8 |
| **Total** | **1662** | **108** |

Los **1451 JVM** comprueban contratos, SQL de host, autoridad capturada, preparación, revisión, cancelación y recuperación. Los **180 Android de datos** incluyen 28 casos nuevos de SQLite real: actor, permisos, colisiones de operación, revisiones concurrentes, recibos exactos, cancelación/guard final, migración aditiva y conservación de errores y bytes.

Los **ocho casos nuevos de app** usan registro/autenticación reales, espacios UUID propios, SQLite y el archivo de asignación aislado. Comprueban autor/corrección con UUID, fecha y motivo; administración sobre un reporte con otro autor guardado dentro de su propio espacio; académico de lectura; giro sin escribir ni cambiar la operación; Cerrado de esquema 4 que migra sin inventar un resultado; cambio de cuenta sin filtrar la revisión; revocación administrativa; y rechazo de un commit con la Activity pausada. Este último llama adversarialmente al modelo después de una revisión real: el guard productivo revierte la transacción, comprobar recibo conserva cero escrituras y un reintento explícito ya visible guarda la misma operación una sola vez. No se simula el backend o el recibo. La asignación administrativa es una precondición técnica del fixture; la confirmación nativa del propietario ya tenía su evidencia en fase 40 y no se vuelve a acreditar aquí.

Las **23 regresiones de app** cubren recorrido guiado 8, estados administrativos 8, consulta protegida 3 sin cambiar PIN y búsqueda 4. Los primeros intentos detectaron fallos del arnés: un chip anidado seguía fuera del viewport al tocarlo y se buscaba semántica después de que la Activity de la cuenta anterior ya se hubiera cerrado. Se corrigieron posición de scroll, comprobaciones de visibilidad/selección y espera del cierre; el clic sigue siendo un toque real. Esos intentos y diagnósticos se conservan, pero no se cuentan como aprobaciones o casos adicionales.

Kotlin debug/release y ensamblados internos debug/test correctos. Lint app **0 errores / 22 avisos existentes por variante**, datos sin incidencias. Recibo `resolution44-test-proof.json` y logs `resolution44-*` ignorados; [procedencia y límites](VALIDACION.md). QA interna SHA-256 `86421a97cb7714559204aacbce5d61d0b75a0b522d92b68d0f9fef7cb32a5b79`, distinta de la APK oficial conservada.

Pruebas en emulador propio aislado API 37 de solo lectura, sin teléfono físico ni nuevos cambios de PIN, llamadas o envíos. El GPS es una fuente controlada para sembrar los reportes, sujeta al permiso nativo; no acredita otra prueba física. Recrear la Activity o un modelo no equivale a matar todo el proceso. La muerte completa del proceso, API 26/fabricantes y aceptación física completa mantienen sus comprobaciones separadas.

## Publicación

Trabajo **local Unreleased**. No cambia la oficial **0.6.2 / código Android 30**, sus archivos, tag o Release. No implica push, integración en main, APK pública o nueva numeración. Login/loading del compañero, ayuda inmediata, GPS/mapa, evidencia y contactos conservan sus recorridos.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Mejoras útiles](MEJORAS_UTILES.md) · [Administrador local](ADMINISTRADOR_LOCAL.md) · [Validación](VALIDACION.md)
