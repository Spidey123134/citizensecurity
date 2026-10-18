# Entrega acumulada 0.6.3

**18 de octubre de 2026 · Android 0.6.3 / código 31 · binario y actualización validados; publicación pendiente.** El usuario pidió revisar GitHub, combinar el trabajo nuevo y publicar otra Release. Esta entrega reúne las fases 43–44 posteriores a la oficial 0.6.2 y la integración revisada del código remoto. [Guía para probar y abrir en Android Studio](PROBAR_0_6_3.md).

Las marcas Unreleased de los cortes de fases 43 y 44 describen su preparación histórica. La solicitud actual autoriza reunirlas en esta entrega. Las mejoras posteriores del recorrido guiado tendrán su propio corte y no se atribuyen a la APK hasta incorporarlas y validarlas.

## Cambios incluidos

| Fase | Cambio |
|---|---|
| [43 / E11](FASE_43.md) | **Recorrido guiado contextual:** icono de ayuda voluntario en Inicio, Reportar, Revisión, Reportes guardados, Detalle y Mapa. Seis temas, 20 pasos, progreso y controles Anterior/Siguiente/Listo/Cerrar. Reabrir empieza desde el inicio; girar conserva el paso. |
| [44 / E12](FASE_44.md) | **Resultado declarado del incidente:** Sin confirmar, Solucionado o Sigue sin solucionarse, independiente de su estado administrativo. Autor real y administrador local autorizado declaran mediante motivo, revisión y otro gesto de confirmación. |
| [44 / E12](FASE_44.md) | **Historial de declaraciones:** UUID de cuenta, rol, fecha, motivo, resultado anterior/nuevo y operación. Corregir agrega otra declaración; no sobrescribe las anteriores. Revisión concurrente y reutilización incompatible de una operación se rechazan. |
| Integración y entrega | Combinación del trabajo local con GitHub, numeración 0.6.3/código 31 y configuración compartida **app** para ejecutar el producto en Android Studio. APK firmada y actualización real desde 0.6.2 comprobadas en emulador propio. |
| Accesibilidad del login | Ajuste del área visible cuando aparece el teclado para que el desplazamiento alcance las acciones inferiores, conservando el diseño y los accesos del compañero. |

Conserva las funciones de 0.6.2: reporte con cámara/galería, punto/GPS a 5 km, calles y mapa, posibles repetidos a 200 m/±2 h, ficha PDF, contactos, actividad, avisos manuales y administración local. El login/loading del compañero conserva su recorrido.

## Decisiones y permisos

**Cerrar un reporte no lo convierte en Solucionado.** Los folios existentes, incluidos los Cerrados, comienzan Sin confirmar si no existe una declaración. Indicar Solucionado tampoco cierra el seguimiento. Son registros académicos locales y no acreditan atención institucional o verificación de lo ocurrido.

El autor se identifica por la cuenta real del evento de creación. El administrador necesita asignación vigente y la capacidad separada `DECLARE_RESOLUTION`; el nombre de usuario no concede ese permiso. Si también es autor, se identifica como Autor. Cada acceso se limita al mismo espacio local capturado de esa cuenta e instalación; no consulta otras cuentas o teléfonos. `admin/admin` y el acceso Desarrollador conservan consulta académica, sin declaración de resultado. [Administración local](ADMINISTRADOR_LOCAL.md).

El motivo requiere **5–500 caracteres válidos**. Revisar y confirmar son gestos separados; se comprueban sesión, autoridad, revisión y ventana activa/desbloqueada antes de guardar. Un resultado incierto conserva la misma operación en el modelo vivo. **Comprobar recibo** solo lee; reintentar requiere otro gesto. No hay diario durable de declaraciones pendientes: al reabrir un proceso se consulta lo realmente guardado, sin repetir automáticamente una escritura. El diario de cambios administrativos mantiene su recorrido independiente.

El tutorial explica las opciones sin guardar reportes, cambiar estados, pedir permisos, iniciar GPS, abrir cámara o enviar mensajes. No copia datos privados a los pasos ni abre la guía automáticamente. Los avisos necesarios para decidir continúan en sus funciones reales.

## Binario y actualización

APK final preparada **citizensecurity-0.6.3-academico.apk**, con **56,151,110 bytes** y SHA-256 `0109149d9f9e6ea1a6883b46fe6915dde7bba9e20fba45c5219dfea6892d1780`. Su archivo de comprobación es **SHA256SUMS-0.6.3.txt**. Ambos se conservan localmente en `.gradle/validacion/release063-final/`. **La publicación de la Release y la descarga verificada continúan pendientes; este documento todavía no acredita un archivo público 0.6.3.**

Paquete `com.example.citizensecurity`, minSdk 26 / targetSdk 37 y certificado académico SHA-256 `0a4006a1d2ef2ad02a0dad7c51b55c0ba5ffcd096a226eed6456326b2fed3ebd`, comprobado e idéntico al de la oficial 0.6.2. **Actualizar encima, sin desinstalar ni borrar datos.** Android rechazando una actualización requiere revisar archivo/firma, conservando la instalación anterior.

La actualización **oficial 0.6.2 → esta APK final 0.6.3** aprobó ambas etapas de `Release063UpgradeTest`: **seed062, un caso; verify063, un caso**, separadas por la instalación real `adb install -r` en un emulador propio API 37. Usó nonce canónico nuevo y el runner real `AndroidJUnitRunner`, sin reemplazar la Application productiva por fixtures.

Antes de abrir el repositorio nuevo se comprobaron bytes idénticos de base de datos, contactos y fotografía. Después se verificaron **migración aditiva de esquema 4 a 5**, integridad de SQLite, foto/contactos intactos, CREATED y tres cambios reales hasta Cerrado conservados, junto con **Sin confirmar, secuencia 0 y ningún evento de resultado inventado**. La prueba conserva sus archivos privados para inspección y no reconstruye la base. Logs: `.gradle/validacion/release063-final/upgrade-seed-final.log` y `upgrade-verify-final.log`.

La prueba manual histórica `ReleaseUpgradeTest` se conserva, con opt-in por nonce para no ejecutarse como una prueba ordinaria. Sus métodos `seed061`/`verify062` solo corresponden al recorrido de sus APK antiguas correctas; no verifican 0.6.3. No se modifica el teléfono físico para estas comprobaciones.

## Evidencia y avance

Después de combinar GitHub se ejecutaron **1077 casos JVM de app**, y Kotlin/ensamblados debug y release/lint terminaron sin errores. Los **216 casos de dominio, 158 JVM de datos y 180 Android de datos** reutilizan sus recibos de fase 44 por identidad comprobada de las fuentes, sin presentarlos como nuevas ejecuciones. Los recorridos Android de app del corte integrado conservan su cierre pendiente hasta completar las últimas regresiones; [Validación](VALIDACION.md) registra el desglose final.

La prueba de actualización real añade sus **dos invocaciones aprobadas** por separado. El cierre histórico de fase 44 conserva **1662 casos distintos / 108 nuevos** y fase 43 sus 26 Android distintos / ocho nuevos; **no se suman esos cortes históricos, las repeticiones o las invocaciones para inventar un total de Release**. No se ha ejecutado otra aceptación física en el teléfono.

El plan original permanece en **28/28 = 100% de construcción funcional local**. E11/E12 amplían utilidad sin aumentar el denominador. Aceptación física completa, API 26/fabricantes, TalkBack, muerte completa del proceso y seguridad/integración institucional necesitan evidencias propias.

[Probar en Android Studio o actualizar](PROBAR_0_6_3.md) · [Fases](FASES.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Validación](VALIDACION.md) · [Entrega anterior](ENTREGA_0_6_2.md)
