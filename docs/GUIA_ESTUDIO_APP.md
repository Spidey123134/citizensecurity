# CitizenSecurity: guía para entender y explicar la aplicación

**Material de estudio · 18 de octubre de 2026.** Describe el código del proyecto, incluida la ayuda contextual local posterior a 0.6.3. Comprueba siempre qué versión estás ejecutando: el código local puede contener cambios todavía ausentes de la APK publicada.

El objetivo es poder recorrer el código, justificar decisiones y reconocer límites. Practica cada explicación con la app abierta y localiza la función que la respalda. Explica qué puedes demostrar y qué necesitas consultar.

## 1. La explicación de un minuto

CitizenSecurity es una aplicación Android académica para registrar incidentes en el teléfono y consultarlos después. Un reporte reúne descripción, tipo, prioridad, fecha, lugar y una fotografía opcional. El usuario revisa los datos antes de guardar y recibe un folio. Puede buscar sus reportes, ver puntos en el mapa, consultar cambios y preparar información para compartir voluntariamente.

La utilidad adicional está en conservar información organizada, dar seguimiento local y facilitar contactos y una ficha PDF. Si existe una emergencia, la ayuda abre el marcador del teléfono para que la persona decida llamar. Guardar un reporte **no lo envía a emergencias, policía o un servidor**. El proyecto no acredita la identidad civil del usuario ni verifica que ocurrió el incidente.

La pregunta clave antes de demostrarlo es: «¿Estoy mostrando un registro local o una comunicación real?». Actualmente el registro y seguimiento son locales. No existe recepción institucional, sincronización entre teléfonos ni un centro policial conectado.

## 2. Cómo está organizado el código

La aplicación usa Kotlin. La interfaz combina pantallas XML —por ejemplo, acceso y carga— con Jetpack Compose en los recorridos de reportes. Compose describe la pantalla mediante funciones marcadas `@Composable`; cuando cambia el estado observado, actualiza la parte correspondiente. XML define una jerarquía de vistas que una Activity enlaza con su comportamiento.

El proyecto tiene tres módulos principales:

| Módulo | Responsabilidad | Ejemplo que conviene abrir |
|---|---|---|
| `app` | Pantallas, navegación, ViewModels y conexión con Android | `CitizenSecurityApplication.kt` |
| `core/domain` | Modelos, reglas y contratos sin interfaz Android | `Report.kt`, `NearbyIncidentPolicy.kt` |
| `core/data` | SQLite, archivos, fotos y adaptadores de ubicación | `SqliteReportRepository.kt` |

Los módulos `verification:location` y `verification:maps` sirven para comprobaciones específicas. Para probar el producto en Android Studio se selecciona **app**, no esos módulos.

Una **Activity** es una entrada a una pantalla o recorrido Android. Un **ViewModel** conserva el estado de ese recorrido durante recreaciones de configuración, como girar el teléfono. No equivale a guardar todo permanentemente: si Android termina el proceso, solo sobrevive lo escrito deliberadamente en almacenamiento o estado restaurable.

Los ViewModels exponen `StateFlow`, un flujo con un valor actual. La pantalla observa valores como «cargando», «listo» o «error» y muestra controles coherentes. Las coroutines permiten expresar operaciones asíncronas; no garantizan por sí solas ejecutarlas fuera del hilo principal. Los repositorios usan `Dispatchers.IO` para trabajo de archivos y base de datos.

Un **repositorio** es la frontera de acceso a datos. La interfaz define operaciones; una implementación concreta decide cómo ejecutarlas. Separar el contrato permite probar reglas y respuestas de la pantalla sin depender siempre de un teléfono o GPS real. `CitizenSecurityApplication` construye esas dependencias: no se necesita buscar un servidor para explicar esta arquitectura.

Referencias: [módulos](../settings.gradle.kts), [dependencias](../gradle/libs.versions.toml), [construcción de dependencias](../app/src/main/kotlin/com/example/citizensecurity/CitizenSecurityApplication.kt), [contrato de reportes](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/ReportRepository.kt).

## 3. Qué sucede desde el formulario hasta el folio

1. La persona elige tipo y prioridad, escribe la descripción y revisa cuándo ocurrió el incidente. La prioridad es una declaración del usuario, no una clasificación policial comprobada.
2. Selecciona el lugar y obtiene una lectura precisa del teléfono mediante un gesto explícito. El punto del incidente y la posición del dispositivo son datos diferentes.
3. Puede tomar una fotografía o elegirla desde el selector. Espera a que termine su preparación.
4. Revisa el borrador completo. Si hay posibles reportes parecidos, ve una advertencia.
5. Al confirmar el guardado, se conserva la identidad del intento, se comprueban reglas y evidencia de ubicación, y se inserta el reporte en SQLite junto a su evento de creación.
6. La pantalla muestra el folio del registro confirmado. Si el resultado es incierto, corresponde recuperar o comprobar ese mismo intento.

`ReportValidator` exige una descripción de 10–1000 caracteres válidos y rechaza fechas anteriores a 1970 o más de un minuto en el futuro. Una referencia escrita, cuando existe, tiene 5–200 caracteres. El contrato general del modelo admite referencia o coordenadas; el flujo real de creación añade el control de cercanía que exige un punto completo y una lectura válida. No confundas la validación general con todas las condiciones del recorrido Android.

Guardar un borrador para continuar después es distinto de crear el reporte. Un **checkpoint** conserva campos del borrador; el registro confirmado tiene folio y evento de creación. La revisión tampoco guarda automáticamente. Esta separación evita que abrir una pantalla o girarla cree incidentes.

Referencias: [modelo](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/Report.kt), [validador](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/ReportValidator.kt), [envío local](../app/src/main/kotlin/com/example/citizensecurity/report/ReportSubmissionViewModel.kt), [borrador](../app/src/main/kotlin/com/example/citizensecurity/report/ReportDraftCheckpointViewModel.kt).

## 4. GPS y mapa: dos funciones distintas

**El mapa dibuja calles y permite elegir un punto. El GPS aporta una lectura del teléfono.** Se pueden ver calles sin haber concedido ubicación; ver un pin no demuestra que la lectura sea reciente ni que se haya guardado un reporte.

La configuración activa utiliza **MapLibre** con el estilo público **OpenFreeMap Liberty**. No utiliza una clave de Google Maps ni una cuenta de facturación Google. La carga del mapa depende de red y del servicio del proveedor; una inicialización local correcta no demuestra que se descargó el estilo. Tampoco hay que confundir nombres de calles dibujados con un servicio de búsqueda de direcciones o geocodificación inversa.

La regla de cercanía permite como máximo **5 km alrededor del teléfono**, teniendo en cuenta su precisión: `distancia + precisión <= 5000 metros`. Por ejemplo, una distancia de 4960 m con precisión estimada de 60 m se rechaza. La lectura debe tener precisión estimada de hasta 100 m, antigüedad máxima de 120 segundos y no aparecer marcada como simulada. El tiempo de antigüedad usa un reloj monotónico para evitar depender de cambiar la hora del calendario.

La distancia se calcula con Haversine, una aproximación esférica. Esta regla reduce reportes hechos desde lugares lejanos, pero no demuestra que un crimen ocurrió ni evita toda manipulación del dispositivo. No expliques el control como un sistema infalible contra mentiras.

La app distingue permiso aproximado y preciso, ubicación desactivada, lectura demasiado vieja y errores. Al guardar vuelve a comprobar la evidencia disponible; el tutorial nunca concede permisos o inicia GPS.

Referencias: [proveedor activo](../app/src/main/kotlin/com/example/citizensecurity/maps/MapsConfiguration.kt), [política de 5 km](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/NearbyIncidentPolicy.kt), [adaptador Android](../core/data/src/main/kotlin/com/example/citizensecurity/data/AndroidPhoneLocationSource.kt).

## 5. Fotos y reportes parecidos

La cámara se abre con el contrato Android de captura y una URI temporal propia. El permiso temporal se limita a ese archivo. Elegir galería y tomar foto terminan en el mismo importador: comprueba el archivo, corrige orientación y produce un JPEG privado sin copiar los metadatos EXIF externos. El registro conserva identidad de foto, dimensiones, tamaño y SHA-256; los bytes se guardan en un archivo privado, no dentro de cada fila del reporte.

SHA-256 permite detectar que los bytes ya no coinciden con la copia preparada. No demuestra quién tomó la foto, cuándo ocurrió el hecho o que la imagen sea auténtica. La copia normalizada tampoco equivale al original forense. Cancelar la cámara debe conservar campos y foto anterior.

La advertencia de parecidos busca **en el espacio local actual** reportes del mismo tipo, hasta **200 m** y dentro de **dos horas antes o después de la hora del incidente**. No compara caras, texto ni contenido visual. Presenta hasta cinco coincidencias y puede advertir que la búsqueda quedó incompleta. Es orientativa: no bloquea guardar ni demuestra que dos personas describieron el mismo hecho. No consulta una base nacional.

Referencias: [fotos](../core/data/src/main/kotlin/com/example/citizensecurity/data/ReportPhotoStore.kt), [captura](../app/src/main/kotlin/com/example/citizensecurity/report/ReportCameraViewModel.kt), [reglas de parecidos](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/SimilarReportQuery.kt).

## 6. Cuentas, sesión y administración

Las cuentas se registran **en este teléfono**. El usuario normal tiene 3–30 caracteres permitidos y contraseña de 15–128 caracteres. No se conserva la contraseña en texto plano: se deriva un verificador con **PBKDF2-HMAC-SHA256**, 600 000 iteraciones y una sal aleatoria de 16 bytes. Al ingresar se deriva de nuevo y se compara. La sal permite que contraseñas iguales no produzcan el mismo verificador.

Esto protege mejor que almacenar texto plano; no significa que todos los archivos estén cifrados ni que un dispositivo comprometido sea seguro. El proyecto no tiene recuperación de contraseña por correo o autenticación en un servidor.

La sesión vive en memoria. Un sello de proceso y revisión permite invalidar operaciones iniciadas antes de salir o cambiar de cuenta, incluso si después vuelve el mismo usuario. Cada UUID de cuenta elige su propia base, borrador, diario y carpeta de fotos. El nombre visible no concede acceso al espacio de otra persona. Esta separación la impone la app sobre almacenamiento privado; no es una base remota multiusuario.

`admin/admin` es un acceso académico reservado; **no concede administración real**. La cuenta `admin_local` se registra con una contraseña propia y requiere confirmar al propietario con el bloqueo nativo de Android y otro gesto para activar la primera asignación. La app no guarda el PIN. Los permisos de consultar, cambiar estado y declarar resultado se comprueban por separado y se releen al operar. La asignación sigue limitada a ese espacio de cuenta; no permite ver otros teléfonos ni convierte a alguien en policía.

Referencias: [reglas de cuenta](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/LocalAccount.kt), [verificador](../core/data/src/main/kotlin/com/example/citizensecurity/data/AccountPasswordVerifier.kt), [sesión](../app/src/main/kotlin/com/example/citizensecurity/account/LocalAccountSession.kt), [partición](../core/data/src/main/kotlin/com/example/citizensecurity/data/ReportStorageNames.kt), [administración](ADMINISTRADOR_LOCAL.md).

## 7. SQLite, estados y resultados

SQLite es una base relacional dentro del teléfono. El esquema de reportes actual es la **versión 5**. Conserva reportes, historial de estados y declaraciones de resultado; las fotografías están vinculadas mediante sus metadatos. Las migraciones desde versiones anteriores añaden estructura sin inventar eventos históricos. La base de cuentas tiene su evolución independiente: «versión 5» no describe todas las bases del proyecto.

Una transacción reúne cambios que deben confirmarse juntos. Crear el reporte y su evento de creación debe ser una operación coherente; un fallo no debe dejar medio historial. Las restricciones SQL complementan la validación Kotlin. El código rechaza esquemas futuros o datos dañados y evita recrear silenciosamente una base vacía como supuesto arreglo.

Hay dos conceptos independientes:

| Concepto | Valores | Significado |
|---|---|---|
| Estado | Reportado → En revisión → Atendido → Cerrado | Seguimiento administrativo local, con avances consecutivos |
| Resultado | Sin confirmar / Solucionado / Sigue sin solucionarse | Declaración sobre si el problema quedó resuelto |

**Cerrado no implica Solucionado.** El autor real, comprobado contra el evento de creación, y un administrador local autorizado pueden declarar el resultado en su espacio. El historial conserva cuenta, rol, fecha y motivo. Corregir agrega una nueva declaración; no elimina la anterior. Si el administrador también es autor, se identifica como autor.

Referencias: [esquema y migración](../core/data/src/main/kotlin/com/example/citizensecurity/data/ReportDatabase.kt), [contrato de resultados](../core/domain/src/main/kotlin/com/example/citizensecurity/domain/ReportResolution.kt), [autoridad](../app/src/main/kotlin/com/example/citizensecurity/account/ReportResolutionAuthority.kt).

## 8. Qué pasa si se pulsa dos veces o se interrumpe

Un UUID identifica un reporte u operación; no es una contraseña. **Idempotencia** significa que repetir la misma operación reconocida no produce otra escritura equivalente. El repositorio compara también sus datos: reutilizar un UUID con otro cuerpo es un conflicto, no un permiso para sobrescribir.

**CAS**, comparar y cambiar, comprueba que el estado o resultado y su secuencia siguen siendo los revisados. Si otra operación avanzó de la secuencia 3 a la 4, una solicitud preparada para la 3 no debe pisar el resultado nuevo. Hay que consultar y revisar otra vez. Es una defensa frente a cambios concurrentes y pantallas desactualizadas.

Un **recibo** es el evento confirmado que coincide con la solicitud completa. Si una respuesta no llega, no se concluye que la escritura falló: puede haberse confirmado. Comprobar recibo solo lee. Reintentar requiere otro gesto y conserva la identidad original.

La recuperación no es idéntica en todos los recorridos: creación y cambios administrativos tienen diarios privados para solicitudes pendientes. La declaración de resultado conserva su preparación en memoria durante el giro; después de terminar el proceso, consulta lo realmente guardado sin reconstruir ni repetir automáticamente la solicitud. Un ViewModel no reemplaza un diario durable.

Referencias: [repositorio SQLite](../core/data/src/main/kotlin/com/example/citizensecurity/data/SqliteReportRepository.kt), [diario de creación](../core/data/src/main/kotlin/com/example/citizensecurity/data/ReportSubmissionStore.kt), [resultado en pantalla](../app/src/main/kotlin/com/example/citizensecurity/resolution/ReportResolutionViewModel.kt).

## 9. Funciones para usar la información

La consulta permite combinar texto, fechas y filtros, buscar un folio y recorrer páginas. El mapa consulta reportes con coordenadas del espacio actual en una zona visible; una muestra limitada no equivale al total de incidentes de una ciudad.

La ficha PDF y el mensaje para un contacto tienen revisión. Preparar el documento no lo envía: compartir exige otro gesto y abre una aplicación externa. La URI del PDF concede lectura a la copia elegida, no acceso a la base privada. Los contactos de confianza se guardan localmente. Ayuda inmediata y los atajos abren el marcador; la persona confirma la llamada. La app no intercepta todos los botones físicos o SOS del teléfono.

Actividad reúne cambios locales reales. Los avisos son optativos y su revisión es manual: no hay un servicio institucional que supervise incidentes en segundo plano. El resumen de Android evita incluir folio, fotografía, ubicación o motivos privados.

El recorrido guiado es ayuda contextual voluntaria con icono de interrogación. Explica una pantalla en pasos cortos; abrirlo, cerrarlo o avanzar no guarda, comparte ni obtiene GPS. Guarda únicamente tema, paso y apertura como estado de interfaz.

Referencias: [PDF](../app/src/main/kotlin/com/example/citizensecurity/evidence/ReportEvidencePdfWriter.kt), [mensaje](../app/src/main/kotlin/com/example/citizensecurity/message/ReportMessageComposer.kt), [marcador](../app/src/main/kotlin/com/example/citizensecurity/help/HelpDialer.kt), [avisos](../app/src/main/kotlin/com/example/citizensecurity/alerts/ReportAlertsViewModel.kt), [tutorial](../app/src/main/kotlin/com/example/citizensecurity/tutorial/GuidedTour.kt).

## 10. Cómo demostrar y depurar sin improvisar

Abre la raíz `citizensecurity` en Android Studio, deja sincronizar Gradle, selecciona **app**, el dispositivo autorizado y Run. El proyecto requiere Android API 26 o superior; actualmente compila y apunta a API 37. El JDK que ejecuta Gradle y el bytecode son cosas distintas: el código declara destino JVM/Java 17. Consulta las versiones de herramientas en los archivos de construcción, no memorices «todo es la última versión».

Antes de presentar, verifica versión instalada, cuenta, red del mapa y ubicación precisa. Usa datos de una prueba identificable. Demuestra guardar, conserva el folio y vuelve a consultarlo; confirmar un pin no prueba el guardado completo. Después muestra una foto, un filtro y un PDF revisado sin compartir datos privados. Los cambios administrativos requieren la cuenta y asignación correctas.

Para depurar, anota acción, resultado esperado, resultado real, versión y pasos repetibles. Logcat y breakpoints ayudan a seguir llamadas; no publiques contraseñas, coordenadas reales o datos sensibles en capturas. Un fallo de GPS, de red del mapa y de SQLite tienen causas distintas. Reinstalar o borrar datos no es el primer arreglo.

Las pruebas JVM comprueban reglas, estados y casos límite; las instrumentadas verifican componentes Android y recorridos con SQLite. El emulador no acredita todo el comportamiento físico. Prueba aprobada tampoco significa ausencia absoluta de errores.

Referencias: [configuración app](../app/build.gradle.kts), [configuración Run](../.run/app.run.xml), [validación y límites](VALIDACION.md).

## 11. Preguntas para practicar y ejercicios

**¿Por qué usar la app si puedo llamar?** Llamar atiende una necesidad inmediata. La app organiza un registro y su seguimiento local; no debe retrasar pedir ayuda.

**¿Dónde están mis datos?** En almacenamiento privado y bases del teléfono, separados por cuenta. El mapa sí utiliza red para sus recursos; eso no convierte los reportes en registros sincronizados.

**¿Por qué no Google Maps?** La configuración activa es MapLibre/OpenFreeMap y no pide facturación Google. La elección mantiene calles y selección de puntos, con dependencia del proveedor de mapas.

**¿Puede un administrador leer todo?** No. Su permiso es local, ligado a su cuenta y espacio; no hay consulta global entre instalaciones.

**¿Qué falta para uso institucional?** Acuerdo con una institución, servidor, autenticación y permisos de producción, recepción y seguimiento comprobables, política de datos y validación operativa. El código actual no demuestra esas capacidades.

Haz estos ejercicios en una rama local y explica el resultado:

1. Encuentra `maxDistanceMeters`. Predice tres casos con precisión incluida antes de probarlos. Cambiar ese número exige revisar pruebas y textos que dicen 5 km.
2. Sigue una descripción desde `NewReport` hasta el `INSERT`. Señala dónde se valida y dónde se confirma la transacción.
3. Explica un giro de pantalla y una muerte de proceso con dibujos distintos. Identifica qué vive en ViewModel, checkpoint y SQLite.
4. Declara un resultado y consulta su historial. Comprueba que el estado del reporte no cambió por esa declaración.
5. Busca el `operationId` de un comando. Describe qué debe ocurrir al repetirlo igual y al repetirlo con otro motivo.
6. Localiza un paso del tutorial, cambia solo su explicación y comprueba que recorrerlo no añadió eventos a la base.

Si no sabes una respuesta, usa: **«No tengo confirmado ese detalle; puedo mostrar el archivo y revisar la implementación o la prueba antes de afirmarlo»**. Distingue lo que entiendes de lo que todavía estás aprendiendo.

## Glosario rápido

**Activity:** entrada a un recorrido Android. **Compose:** interfaz declarativa. **ViewModel:** estado y coordinación de una pantalla. **StateFlow:** valor observable actual. **Coroutine:** tarea suspendible. **Repositorio:** contrato de acceso a datos. **SQLite:** base relacional local. **Transacción:** confirmación conjunta de operaciones. **Migración:** cambio de estructura conservando datos. **UUID:** identidad de registro u operación. **Idempotencia:** repetición sin duplicar el efecto reconocido. **CAS:** escritura condicionada a la revisión esperada. **Recibo:** evento confirmado coincidente. **Hash:** huella derivada de bytes; no prueba de autoría. **Workspace:** espacio de datos y acceso capturado para una sesión. **Checkpoint:** copia explícita para retomar un borrador.
