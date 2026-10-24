# Entrega acumulada 0.7.0

**21 de octubre de 2026 · Android 0.7.0 / código 32 · Release oficial publicada y descarga verificada.** Esta versión reúne **todos los cambios posteriores a la oficial 0.6.3: fases 45–47 y el pulido de esta entrega**. La [Release oficial v0.7.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.7.0), ID **419228925**, se publicó el **21 de octubre de 2026 a las 06:32:51 UTC**, sin marca de borrador ni prerelease. Los dos archivos se descargaron de nuevo y sus bytes y SHA-256 coinciden con los archivos finales y los digests de GitHub.

Las marcas Unreleased y los números de versión de los documentos anteriores describen sus cortes históricos. No significan que se excluya ese trabajo de la entrega acumulada. La [oficial 0.6.3](ENTREGA_0_6_3.md), su tag y sus archivos conservan su identidad.

## Cambios incluidos

| Origen | Cambio respecto de la APK oficial 0.6.3 |
| --- | --- |
| [Fase 45 / E11](FASE_45.md) | Ayuda voluntaria junto a **Resultado del incidente**, con cuatro pasos sobre resultado y seguimiento, actor/rol/fecha/motivo, revisión/confirmación e historial/recibo. El conjunto pasa de seis temas/20 pasos a **siete temas/24 pasos**. |
| [Revisión 46](REVISION_46.md) | Ajustar el pin conserva la lectura GPS visible de la misma apertura; confirmar sigue comprobando vigencia, precisión, simulación y distancia máxima de **5 km**. Salir de la pantalla exige renovar la evidencia. |
| [Revisión 46](REVISION_46.md) | Consultar el historial administrativo del mismo folio conserva el motivo y la declaración preparados o revisados. Otro folio, error o acceso revocado siguen retirando esa preparación. |
| [Revisión 46](REVISION_46.md) | El acceso alternativo se identifica como **Acceso académico sin cuenta**, separado de las cuentas registradas y sin permisos administrativos. Limpieza de comentarios redundantes conservando el diseño e historial del proyecto. |
| [Fase 47](ACEPTACION_47.md) | Contactos conserva nombre y teléfono pendientes al girar mediante el ViewModel; no copia esos campos al Bundle ni los guarda por abrir o girar la pantalla. Cambiar de cuenta retira esa preparación. |
| [Fase 47](ACEPTACION_47.md) | Los mapas comprueban **OpenGL ES 3.0** antes de inicializar el dibujante o crear la vista nativa. Un dispositivo incompatible recibe una explicación y puede volver conservando el formulario o consultar sus reportes en la lista. |
| [Fase 47](ACEPTACION_47.md) | Teclado de texto para usuario, etiquetas asociadas a usuario/contraseña y orientación del mapa que recomienda localizar primero el teléfono. Se conserva el diseño del login/loading del compañero. |
| Pulido 0.7.0 | La preparación de un contacto se conserva hasta confirmar su guardado. Ante un fallo o respuesta perdida se comprueban los contactos por lectura; solo la coincidencia exacta de identidad, nombre y teléfono confirma esa alta. Una preparación posterior permanece intacta y no hay reintento automático o traslado a otra cuenta. |
| Material de estudio | [Guía mantenible](GUIA_ESTUDIO_APP.md) para comprender arquitectura, guardado, ubicación, fotografías, cuentas, resultados y recuperación, con referencias al código, preguntas y ejercicios. |

Conserva el reporte con cámara/galería, mapa con calles y pines locales, advertencia de posibles repetidos a 200 m/±2 h, ficha PDF, contactos, ayuda inmediata, actividad, avisos manuales y administración local. **No incorpora un servidor o sincronización entre teléfonos**; el alcance del servidor continúa pendiente de decidir.

## Decisiones que se conservan

Guardar un reporte produce un registro en este teléfono y un folio; no lo envía a emergencias, policía o una institución. Los mapas muestran reportes del espacio local de la cuenta. La cercanía del teléfono no acredita que ocurrió el incidente.

**Cerrado y Solucionado son conceptos independientes.** El resultado declarado conserva quién actuó, su rol, fecha y motivo en un historial aditivo. El autor real y el administrador local autorizado necesitan permisos vigentes dentro del mismo espacio; el nombre de una cuenta no concede autoridad. `admin/admin` y el acceso académico permiten el recorrido académico, sin declaración de resultado ni administración de otras cuentas. [Administración local](ADMINISTRADOR_LOCAL.md).

Revisar y confirmar son gestos separados. Comprobar un recibo consulta lo guardado; reintentar requiere otro gesto y la misma operación. Las declaraciones de resultado pendientes viven en memoria: un proceso nuevo consulta SQL sin reconstruir o repetir esa solicitud. El diario durable de cambios administrativos conserva su recorrido independiente.

La guía es general y voluntaria: no copia datos privados, guarda cambios, solicita ubicación, abre la cámara o envía mensajes por consultar sus pasos. Puede cerrarse, recorrerse de nuevo y conservar el paso al girar.

## Compatibilidad gráfica

El producto mantiene **Android 8/API 26 o superior**; sus mapas requieren además **OpenGL ES 3.0**. La declaración del manifest sirve como información de compatibilidad, pero una instalación directa de APK no comprueba por sí sola ese requisito. Por eso también existe una guarda antes de crear ambos mapas. [Comprobación y fuentes del requisito](ACEPTACION_47.md#requisito-gráfico-y-error-recuperable).

El cierre histórico de fase 47 probó un AVD API 26 con GLES 3.1 para el recorrido completo y con GLES 2 para comprobar el rechazo seguro. Una GPU anfitriona compatible no basta si el Android invitado anuncia solamente GLES 2. La explicación de incompatibilidad no representa un mapa cargado ni permite confirmar un punto sin evidencia válida. No se validan con ello todos los controladores o fabricantes.

## Binario y actualización

| Dato final | Entrega publicada |
| --- | --- |
| Versión Android | 0.7.0 / código 32 |
| Paquete | `com.example.citizensecurity` |
| API mínima / objetivo | 26 / 37 |
| APK publicada | [citizensecurity-0.7.0-academico.apk](https://github.com/Spidey123134/citizensecurity/releases/download/v0.7.0/citizensecurity-0.7.0-academico.apk) |
| Bytes y SHA-256 de la APK final | **55,617,944 bytes** · `6a312b1986fd4e6d6aff9133eade87d0847d25a80714cdd175c805f9ccd5a1d9` |
| Firma de actualización | Certificado académico comprobado, idéntico al de 0.6.3: SHA-256 `0a4006a1d2ef2ad02a0dad7c51b55c0ba5ffcd096a226eed6456326b2fed3ebd` |
| Archivo de comprobación publicado | [SHA256SUMS-0.7.0.txt](https://github.com/Spidey123134/citizensecurity/releases/download/v0.7.0/SHA256SUMS-0.7.0.txt) |
| Bytes y SHA-256 del archivo de comprobación | **103 bytes** · `b9cf5ecafa8dce0de92f59f6d7453198bc827f7a57aaee92f53e49fc240241a3` |
| PR / commit de integración | [PR #34 integrado en main](https://github.com/Spidey123134/citizensecurity/pull/34) · `7ce0b802e22bffa9dc9a0c92ed48481bff269aaa` |
| Tag / Release | [v0.7.0 · Release 419228925](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.7.0), sobre el commit de integración |
| Descarga verificada | Ambos archivos: bytes, SHA-256 y digests de GitHub coincidentes; recibo del **21 de octubre de 2026 a las 06:33:32 UTC** |

La actualización se instala **encima de 0.6.3, sin desinstalar ni borrar datos**, conservando paquete y certificado. La firma de la APK final coincide con el certificado académico anterior. Los archivos finales y los recibos de esta entrega están en `.gradle/validacion/release070-final/`; sus bytes/hash no deben confundirse con los candidatos internos anteriores. `published-release.json` conserva los datos de la Release publicada y `published-download-receipt.json` registra la comprobación de ambas descargas, el commit del tag y los digests coincidentes.

La aceptación nueva `Release070UpgradeTest` **aprobó sus dos etapas en un emulador propio API 37**, con el runner `AndroidJUnitRunner` y la Application productiva real: `seed063` sobre la APK oficial 0.6.3, instalación real `adb install -r` de la APK final 0.7.0 y `verify070`. Nonce `06e839e8-1b22-4970-b8c3-860528612fb7`; PID de siembra **4623**, PID posterior **4700**. No se desinstaló ni borró almacenamiento entre etapas.

Antes de abrir los repositorios nuevos se comprobaron bytes idénticos de SQLite, fotografía, contactos, checkpoint y solicitud durable pendiente. Después se verificaron integridad y contenidos: **una fila de reporte, cuatro eventos de seguimiento y una declaración de resultado** conservados. La solicitud pendiente sigue en su archivo y **no produce otra fila SQL por actualizar**. El esquema continúa en **5**, sin reconstrucción ni una migración inventada. La entrada normal del launcher también conserva los datos. Logs: `release070-final/upgrade-seed.log`, `upgrade-verify.log`, `upgrade-system-out.log` y `upgrade-receipt.json`.

Las pruebas históricas `ReleaseUpgradeTest` y `Release063UpgradeTest` conservan su relación con sus APK antiguas. No acreditan la actualización 0.6.3 → 0.7.0 por ejecutarse con una versión distinta.

## Evidencia de esta entrega

**1507 casos distintos aprobados, cero fallos, errores u omitidos finales.** Se ejecutaron **1461 pruebas JVM** —dominio 216, datos 158 y app 1087— y **61 ejecuciones Android correspondientes a 46 definiciones distintas**. Las mismas definiciones ejecutadas en dos plataformas se cuentan una vez. El total no incluye recibos históricos reutilizados, el intento fallido del harness ni repeticiones como aprobaciones adicionales; tampoco significa que sean 1507 definiciones nuevas.

| Capa o recorrido | Ejecución de 0.7.0 | Procedencia y límites |
| --- | --- | --- |
| Dominio JVM | **216 aprobadas** | Ningún recibo reutilizado en esta fila |
| Datos JVM | **158 aprobadas** | Ningún recibo reutilizado en esta fila |
| App JVM y pulido de contactos | **1087 aprobadas**; clase Contactos **19**, cuatro casos nuevos | No atribuir el corte de fase 47 a esta ejecución |
| App Android API 26 | **12 recorridos y cuatro casos de permisos aprobados** | Corte actual: lote de UI de 83.695 s; permisos ejecutados por separado |
| App Android API 37, interfaz | **35 casos distintos aprobados:** 34 del lote y uno en repetición dirigida | La repetición corrigió únicamente el desplazamiento del harness; la APK del producto conserva su hash |
| App Android API 37, permisos | **Cuatro casos aprobados** | Ejecuciones separadas con sus permisos/estado GPS preparados |
| Actualización real 0.6.3 → 0.7.0 | **Dos etapas aprobadas**, API 37 | No sustituible por una actualización anterior |
| Recuperación en otro proceso | **Dos etapas aprobadas**, API 37 | PID **5709 → 5829**; cierre entre invocaciones, no interrupción durante commit |
| Mapa incompatible | **Dos casos aprobados**, API 26/GLES 2 | Selección y consulta sin crear el mapa nativo, con salida útil |
| Kotlin, ensamblados, firma y lint | **Debug/release correctos**; lint cero errores y **22 avisos anteriores por variante** | Firma del nuevo archivo comprobada contra 0.6.3 |

El error de contactos se reprodujo antes del arreglo: el corte rojo ejecutó 18 casos y obtuvo tres fallos. Tras el pulido pasan los 19 casos de esa clase, incluidos cuatro nuevos. Se comprueban fallo anterior a la escritura, respuesta perdida después del guardado, preparación modificada durante la operación y rechazo de una coincidencia con identidad o cuerpo distintos. La preparación solo se retira cuando queda confirmado su propio contacto; comprobar por lectura no repite la escritura.

El lote API 37 obtuvo 34 aprobaciones y un fallo del harness: después de desplazar la lista a la descripción, un botón virtualizado de la `LazyColumn` ya no estaba compuesto para el clic. El folio y la descripción eran correctos; el fallo ocurrió antes de pulsar esa acción. La corrección se limitó al test: volver al índice 0 mediante `performScrollToIndex(0)` antes de buscar y pulsar el botón. Se repitió **únicamente ese caso**, que pasó ejecutando ambos clics reales; log `release070-map-return-api37-final.log`. La APK del producto conserva SHA-256 `6a312b1986fd4e6d6aff9133eade87d0847d25a80714cdd175c805f9ccd5a1d9`. El intento fallido permanece como diagnóstico y no se suma a los casos finales aprobados.

Los cuatro casos de permisos pasan tanto en API 26 como en API 37. La aceptación de dos procesos API 37 pasó con PID distinto, datos y recibos conservados y sin repetir automáticamente las operaciones. La selección y consulta del mapa también pasan el rechazo seguro en API 26/GLES 2. Son ejecuciones de esta entrega; sus resultados no se infieren del corte histórico de fase 47.

El desglose de Android por plataforma es **43 ejecuciones API 37** —34 aprobadas en el lote de interfaz, una repetición dirigida aprobada, cuatro permisos, dos etapas de proceso y dos etapas de actualización— y **18 API 26** —12 recorridos y cuatro permisos con GLES 3, más dos casos con GLES 2—. Son **61 ejecuciones finales**, con 46 definiciones distintas; no se añade el intento de interfaz fallido a ese total.

Recibo enumerado `.gradle/validacion/release070-final/release-proof.json`, con identidades, resultados y procedencia del binario. Logs principales: `release070-build-unit-final.log`, `release070-contacts-red.log`, `release070-native-build-lint.log`, `release070-ui-api26-final.log`, `release070-ui-api37-final.log`, `release070-map-return-api37-final.log`, `release070-permissions-api*-*.log` y `release070-process-api37-{seed,verify}.log`; los logs de actualización están en `release070-final/`. Kotlin debug/release, ensamblados debug/release y firma correctos; lint cero errores y 22 avisos anteriores por variante. La integración, publicación y descarga verificada están cerradas con la identidad de la tabla del binario.

El runner habitual quedó restaurado, al igual que los ajustes de IME, letra y ubicación. Los AVD propios 5568/5570 quedaron cerrados conservando sus carpetas; el teléfono físico permaneció intacto. Estas tareas de cierre no añaden ejecuciones a los resultados anteriores.

Los resultados históricos siguen consultables: [fase 45](FASE_45.md), [revisión 46](REVISION_46.md), [fase 47](ACEPTACION_47.md) y [validación general](VALIDACION.md). La última fase comprobó recuperación entre procesos con PIDs diferentes y bytes conservados; ese cierre no mató el proceso durante un commit ni comprobó pérdida de energía.

El plan conserva **28/28 = 100% de construcción funcional local**. La aceptación física completa, cámara/GNSS físicos, TalkBack, una persona nueva y seguridad/integración institucional siguen siendo comprobaciones separadas. Una Release no convierte esos pendientes en completados.

[Probar 0.7.0](PROBAR_0_7_0.md) · [Fases](FASES.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Validación](VALIDACION.md) · [Entrega anterior](ENTREGA_0_6_3.md)
