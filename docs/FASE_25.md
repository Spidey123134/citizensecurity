# Fase 25 — cuentas locales y acceso comprobado

Estado: **Unreleased para 0.6.0**, código en main. Sin APK, incremento de versión, tag o Release. La última entrega binaria continúa siendo 0.5.7 / código 27.

## Recorrido conectado

El compañero construye solo la interfaz del login; esta fase conecta la lógica a sus layouts existentes, sin modificar `activity_main.xml`, `activity_createaccount.xml` ni splash.

1. **Crear cuenta** abre el layout existente y registra un usuario por gesto explícito.
2. Usuario de 3 a 30 letras ASCII, números o guion bajo; se eliminan espacios exteriores y se normalizan mayúsculas. `admin` queda reservado para el acceso académico.
3. Contraseña de 15 a 128 caracteres Unicode válidos. Se conserva exactamente, incluidos espacios y acentos; no se recorta, normaliza o trunca. No se admiten únicamente espacios ni sustitutos Unicode incompletos.
4. El registro vuelve al login con el usuario, sin iniciar sesión automáticamente. Es necesario escribir la contraseña y pulsar **Iniciar sesión** para comprobarla.
5. **Inicio** muestra la cuenta local activa y ofrece **Cerrar sesión**. Volver desde Inicio también termina esa sesión. Cerrar sesión conserva cuentas, reportes, fotografías y diarios.

Las cuentas pertenecen exclusivamente a este teléfono. No son una cuenta de servidor ni se sincronizan. Los reportes anteriores y nuevos continúan compartidos por dispositivo: la pantalla lo explica y no los denomina reportes propios. `admin/admin` y Desarrollador siguen siendo acceso académico público, sin cuenta real ni permisos administrativos; ambos limpian cualquier identidad local previa.

## Datos y credenciales

`AccountRepository` separa registro y comprobación de credenciales. `LocalAccount` expone únicamente UUID y usuario normalizado. `SqliteAccountRepository` utiliza `citizensecurity_accounts.db`, esquema 1, independiente de la base de reportes esquema 2. No atribuye datos históricos a un usuario ni altera sus contratos.

Cada contraseña se deriva mediante PBKDF2-HMAC-SHA256, 600000 iteraciones, sal aleatoria de 16 bytes y resultado de 32 bytes. Se almacena un verificador versionado, nunca la contraseña en texto plano; la comparación usa `MessageDigest.isEqual`. La selección utiliza el algoritmo incorporado en Android API 26+, sin añadir dependencias. El coste y la política de longitud se contrastaron con [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) y [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html); la disponibilidad consta en [SecretKeyFactory de Android](https://developer.android.com/reference/javax/crypto/SecretKeyFactory).

El registro valida parámetros, usa transacción y restricción UNIQUE, y nunca sustituye una cuenta existente. KDF/SQLite trabajan fuera de Main; se revalida cancelación antes de insertar y confirmar. Un usuario inexistente consume el mismo coste de derivación sin crear una cuenta o archivo. Los verificadores incompatibles, versiones de base desconocidas o corrupción producen error; no se borran o recrean datos para ocultarlo.

`AccountSql.CREATE_TABLE`, `INSERT` y `LOOKUP` son las mismas constantes que usan producción y la prueba SQLite del host. El esquema comprueba UUID, usuario canónico, unicidad y límites; los argumentos se vinculan como valores literales.

## Ciclo de vida

`AccountAccessViewModel` reserva una única operación antes de publicar Loading. Los dobles toques no duplican registro o ingreso. Las generaciones y cancelación rechazan respuestas de operaciones abandonadas; consumir el éxito requiere el objeto vigente y ocurre una sola vez.

La UI navega únicamente estando RESUMED. Girar conserva la operación del modelo; salir o enviar la app al fondo la cancela. Una escritura interrumpida puede haberse confirmado: el aviso pide intentar ingresar antes de registrar otra vez. No se reintenta automáticamente ni se confunde un fallo de almacenamiento con contraseña incorrecta.

Los campos de contraseña excluyen el guardado del estado de la jerarquía y se vacían al enviar/salir. No se incluyen contraseñas en intents, resultados, estado del modelo, SavedStateHandle o logs. El resultado de registro devuelve solamente el usuario. `LocalAccountSession` mantiene la identidad en memoria: tras perderse el proceso hay que ingresar nuevamente. Esta sesión no es un token, rol ni autorización para aislar reportes.

## Comprobaciones ejecutadas

**36 casos nuevos distintos aprobados, cero fallos, errores u omitidos:** 8 de validación/identidad, 8 criptográficos, 6 SQLite del host, 12 de coordinación del modelo y 2 de sesión. La repetición de sesión durante la compilación final no aumenta el total.

Los casos criptográficos incluyen sal distinta, contraseña exacta con Unicode/espacios, parámetros corruptos y un vector PBKDF2 independiente. SQLite usa archivos temporales y el SQL productivo para comprobar reapertura, duplicados sin sustitución, restricciones, rollback, lectura ausente sin cambios y argumentos literales. El modelo comprueba dobles gestos, resultado consumido una vez, errores, cancelación, respuesta tardía, sustitución y destrucción.

Kotlin debug/release y pruebas Android de app/datos compilados. Lint: **app 0 errores/22 advertencias previas; datos 0 errores/0 advertencias**. `git diff --check` aprobado. Resultados locales en `.gradle/validacion/fase25-resultados`; logs de cierre `fase25-validacion.log` y `fase25-sql-validacion.log`.

**12 casos Android preparados y compilados, no ejecutados:** 9 del repositorio y 3 del recorrido visual. Cubren almacenamiento Android/reapertura, concurrencia, cancelación, corrupción, registro→login→Inicio→cierre, contraseña excluida tras recreación y acceso académico sin identidad. La prueba SQL del host no sustituye SQLiteOpenHelper, proveedor criptográfico ni navegación Android. Esa aceptación queda para la entrega agrupada 0.6.0, sin generar APK por esta fase.

## Plan y siguiente bloque

**17/28 = 60.7% de construcción**, con los mismos criterios/pesos del PDF. Usuarios pasa a **3/4** por pantalla, creación local y comprobación de acceso; aceptación Android pendiente. Reportes 4/4, GPS 4/4, consulta 2/4, mapas 4/4, administración 0/4 y notificaciones 0/4.

Falta identidad del autor y aislamiento de sus reportes: es el siguiente bloque de 4.1/4.4. Habrá que conservar los reportes históricos compartidos sin inventar autoría y coordinar borradores/solicitudes duraderas con la cuenta activa. Administración, recuperación de contraseña, servidor/sincronización y notificaciones no se acreditan en esta fase. El compañero conserva los layouts y sus IDs `user`, `pass`, `login_button`, `create_account_link` y `login_link` como contrato de integración.
