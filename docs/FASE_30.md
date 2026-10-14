# Fase 30 — ayuda y contactos de confianza

Estado: **desarrollo Unreleased posterior a 0.6.0**. El usuario confirmó **proyecto académico en México**. Android conserva **0.6.0 / código 28**, sin nueva APK publicada, tag o Release. La APK oficial 0.6.0 corresponde al corte anterior.

## Utilidad y recorrido

**Inicio → Ayuda y contactos** permite abrir el marcador sin completar un reporte ni obtener ubicación. En México, 911 atiende emergencias y 089 permite denuncia anónima, según la [Secretaría de Seguridad y Protección Ciudadana](https://www.gob.mx/sspc/es/articulos/sabes-cual-es-la-diferencia-entre-los-numeros-088-089-y-911). La pantalla diferencia ambos accesos y ofrece los contactos que hayas elegido.

1. Pulsa **Abrir marcador · 911 emergencias**, **Abrir marcador · 089 denuncia anónima** o **Abrir marcador** en un contacto.
2. Revisa el número en la aplicación de teléfono y pulsa llamar si deseas hacerlo. CitizenSecurity abre ACTION_DIAL; no inicia la llamada.
3. Para guardar un contacto, escribe nombre y teléfono y pulsa **Guardar contacto**. Puedes conservar hasta cinco en tu espacio.
4. **Eliminar contacto** pide confirmación; solo elimina esa entrada de CitizenSecurity. **Actualizar contactos** vuelve a leer el archivo local.

Los nombres admiten de 1 a 60 caracteres Unicode válidos y los teléfonos de 10 a 15 dígitos, con + inicial opcional. Se admiten espacios, guiones y paréntesis como formato de entrada y se guarda el número normalizado. Los accesos 911/089 son botones separados; no se guardan como contactos de longitud corta. La validación impide teléfonos duplicados y comandos de URI en el número.

## Privacidad y recuperación

Cada cuenta local conserva un archivo privado propio. El modo académico tiene un archivo distinto, compartido entre quienes usan ese acceso en el mismo teléfono; para contactos personales se ofrece la cuenta local. Cerrar sesión conserva el archivo y revoca la pantalla anterior. No se lee ni modifica la agenda del teléfono.

La lectura y los cambios usan un archivo acotado, validación estricta, AtomicFile y revisión CAS para evitar reemplazar cambios nuevos desde una lectura vieja. Un error al leer mantiene el archivo y bloquea cambios; nunca se interpreta como lista vacía. Un guardado/eliminación cuyo resultado no se pudo confirmar exige volver a leer antes de otro cambio. Las respuestas tardías y los gestos de una sesión vencida se descartan.

Abrir el marcador exige gesto, resultado vigente y Activity RESUMED. Si no existe aplicación de teléfono o falla la apertura, la pantalla explica el problema y deja el número visible para usar otro teléfono. Los accesos de emergencia permanecen disponibles aunque falle la lectura de contactos. No se solicitan permisos de llamadas, SMS, agenda o GPS para este recorrido.

## Alcance académico

Abrir el marcador no informa por sí mismo el incidente, confirma una llamada o garantiza atención. Guardar un reporte tampoco envía avisos a policías o servicios de emergencia. La [ficha del incidente](FASE_29.md) permite organizar información y entregarla voluntariamente después, según las opciones revisadas por el usuario.

No hay integración institucional confirmada, despacho automático o receptor policial en esta fase. La asignación administrativa sigue pendiente; admin/admin conserva el acceso académico. Las funciones futuras de institución, recepción y seguimiento se acordarán antes de conectarlas a controles reales.

## Comprobación y avance

La comprobación técnica cubre validación de nombres/teléfonos, límite y duplicados, aislamiento por dueño, archivos inválidos, CAS, incertidumbre y lectura posterior, revocación de sesión, dobles gestos e intención ACTION_DIAL. Los resultados ejecutados se registran en [VALIDACION](VALIDACION.md); las pruebas de apertura del marcador no realizan llamadas ni prueban la atención de los números.

**19/28 = 67.9% permanece**, con los mismos criterios/pesos del PDF. Esta extensión aporta acceso y contactos; no acredita 4.7 Notificaciones, recepción policial o seguimiento administrativo. [Plan actualizado](PLAN_DEL_PROYECTO.md) · [Fases](FASES.md).
