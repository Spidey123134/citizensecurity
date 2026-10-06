# Probar las funciones visibles de 0.4.0a

La versión **0.4.0a / código Android 19** conserva las herramientas básicas de 0.3.1: recepción, validación, coordenadas y ejemplo ficticio en memoria. Integra los layouts actuales del compañero y recupera los controles del ejemplo que faltaban en su nuevo diseño. El título interno de la base sigue en 0.3.1; no es la versión instalada del APK.

El puente de ubicación y el borrador se publican como código listo para la futura conexión visual; todavía no tienen controles en la app. Guardado real y consulta continúan ocultos. [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a), [Fases](FASES.md) y [Validación](VALIDACION.md) registran el estado oficial y las limitaciones. La publicación histórica v0.4.0 se conserva, sin borrar ni reescribir sus notas.

## Entrar

- Pulsa **Desarrollador · funciones básicas** en el inicio.
- También puedes escribir usuario **admin**, contraseña **admin**, y pulsar **Iniciar Sesión**.

Ambas rutas abren **Funciones básicas · 0.3.1**: recepción, validación, coordenadas y el ejemplo ficticio de guardado. Este acceso es una demostración local pública: no crea cuentas, sesiones de servidor ni permisos administrativos. La contraseña se borra del campo al entrar.

## Probar

1. Modifica categoría, prioridad, descripción, fecha del incidente, referencia escrita o coordenadas. La fecha usa ISO-8601 UTC, por ejemplo `2026-10-03T12:00:00Z`; al abrir se propone una fecha reciente.
2. **Ver datos recibidos** convierte la entrada a `NewReport` y muestra los datos. Una fecha o coordenada no convertible muestra error por campo; este botón no afirma que el borrador sea válido.
3. **Validar reporte** aplica `ReportValidator`. Un borrador correcto muestra confirmación; uno inválido indica los campos a corregir.
4. **Probar ubicaciones** demuestra un punto válido sin referencia, coordenadas incompletas y latitud fuera de rango. Compara después estos ejemplos con tus propias coordenadas.
5. **Ejemplo de guardado** muestra un reporte ficticio con folio, categoría, prioridad, fecha, descripción, referencia y coordenadas. El resultado aparece aparte de recepción y validación.
6. **Volver al inicio** regresa al login y descarta el resultado del ejemplo de esa sesión.

Los campos y los resultados se conservan al recrear la actividad por rotación. Recepción, validación y coordenadas no insertan reportes. El nuevo ejemplo sí realiza un guardado ficticio, exclusivamente en SQLite en memoria (`name = null`): no crea archivos de base de datos, no toma lo escrito en el formulario y no usa la base productiva ni servicios externos. La base se cierra antes de presentar el reporte; solo su resultado permanece en el modelo. Los datos descriptivos son fijos, el folio se genera y las fechas se obtienen del reloj del ejemplo.

Mientras el ejemplo está ejecutándose se bloquea otra solicitud. Si ya terminó y se pulsa otra vez, se muestra el mismo resultado y folio sin otra inserción hasta salir de las herramientas. Cambiar el formulario no cambia el ejemplo. Se puede rotar y conservar ambos resultados sin mezclar sus datos.

## Funciones ocultas y trabajo futuro

El guardado de datos reales y toda la consulta de 0.4 permanecen ocultos en la interfaz, tanto en debug como en release. No hay botones para guardar el formulario o consultar un folio. **0.3.0 y el pulido 0.4.0 siguen Unreleased**. Su lógica se conserva y parte ya existe en código público y APK anteriores; ocultar accesos no vuelve privada la fuente ni retira las versiones anteriores.

Las tareas técnicas `:core:data:guardarReporte` y `:core:data:consultarReporte` siguen disponibles para comprobar contratos en bases de prueba con un emulador o teléfono. No son rutas habilitadas para el usuario de la aplicación ni usan su base productiva. [Fase 3](FASE_3.md) y [Fase 4](FASE_4.md) describen esas comprobaciones.

El compañero puede preparar las pantallas futuras, pero debe mantener sin conectar los eventos de guardado real y consulta hasta una instrucción precisa. El ejemplo usa `SaveExampleViewModel.runExample()` y `TemporaryReportSaveExample.run()` sin recibir un borrador; no debe sustituirse por `ReportViewModel.save` ni por el repositorio productivo.

La comprobación real en el emulador aprobó el acceso por botón y admin / admin, las herramientas anteriores, el ejemplo independiente del formulario, la conservación de resultados al rotar y el mismo folio al repetir. Al salir y volver, el ejemplo espera una ejecución nueva. El listado y los hashes de las bases productivas permanecieron iguales. La evidencia completa está en [Validación](VALIDACION.md).

El mapa real y la autenticación siguen pendientes. [Fases](FASES.md) y [Validación](VALIDACION.md) distinguen la preparación local, el historial publicado y las comprobaciones actuales. Terminar o probar no autoriza crear commits, subir, integrar ni publicar.
