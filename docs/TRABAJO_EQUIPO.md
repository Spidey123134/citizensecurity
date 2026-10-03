# Trabajo del equipo

## Reparto inicial

| Responsable | Área | Archivos |
| --- | --- | --- |
| Compañero (`vazdavr-sudo`) | Interfaces, menú, diseño, navegación y login; futura vista de Google Maps con pin y confirmación | `app/src/main`, excepto la composición compartida de dependencias; la conexión del mapa y el login se coordinan al definir ese alcance |
| Nuestra parte | Validar, guardar y consultar reportes locales; preparar sus modelos para la interfaz | `core/domain`, `core/data` y `app/.../report`, con sus pruebas |
| Ambos | Contratos, manifiesto, herramientas y cambios que conecten las dos áreas | Se coordinan antes de modificar |

El compañero añadió `SplashActivity` y el layout visual de login en `7e90109`, y completó `activity_splash.xml` en `e18e636`, ya incorporado desde `main`. La autenticación permanece pendiente. El trabajo de datos conserva sus cambios y no añade pantallas, login ni navegación por iniciativa propia.

El acoplamiento solicitado se realiza con `ReportViewModel` en `app`, una fábrica que reutiliza el repositorio de `CitizenSecurityApplication` y la propiedad `MainActivity.reportViewModel`. El modelo recibe `NewReport` y devuelve estados de progreso, errores por campo o el reporte guardado. La guía [Acoplar la interfaz](ACOPLAR_INTERFAZ.md) permite conectar el futuro formulario sin acceder a SQLite desde la pantalla. El diseño, los eventos de los controles y la autenticación corresponden al compañero.

El avance de consulta incorpora `ReportQueryViewModel`, su fábrica y `MainActivity.reportQueryViewModel`, reutilizando el mismo repositorio. La [fase 4](FASE_4.md) documenta los estados y la llamada para que el compañero conecte después su control de folio. Nuestra parte no añade una interfaz de consulta.

La guía [Inicio del equipo](INICIO_EQUIPO.md) explica cómo clonar la base y comenzar desde una rama propia. Los estados de cada paso se mantienen en [Fases de trabajo](FASES.md) y los cambios entregados en el [Changelog](../CHANGELOG.md). Una prueba técnica aprobada no sustituye la revisión del usuario sobre el comportamiento de la función.

## Incremento actual y ubicación con mapa

La entrega oficial de la línea `0.2` es **0.2.1**, código Android **6**, sobre `main` actualizado. Conserva la interfaz del compañero, las correcciones y el puente técnico de los PR #4 y #5. [Release v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1).

El usuario autorizó el botón **Desarrollador · funciones 0.2**, la pantalla sencilla de herramientas y el acceso local **admin / admin** para probar funciones publicadas. Es una excepción expresa al reparto inicial: el compañero conserva el diseño, la navegación general, el formulario y la futura autenticación real. La demostración no crea cuentas, sesiones ni permisos administrativos.

**Fase 3 / 0.3.0 y el avance 0.4.0 siguen en Unreleased**. Las herramientas oficiales no presentan botones de guardado ni consulta. 0.4 continúa en su rama, actualizada con la base oficial, con código Android **7**. Conserva el avance `474d07c` y prepara consultar por folio y recuperar los datos y el estado local sin modificar registros. Véase [Fase 4](FASE_4.md) y [Plan del proyecto](PLAN_DEL_PROYECTO.md), basado en las secciones reales del PDF. La integración en main y publicación siguen pendientes.

La entrega oficial `0.2.1` aprobó 14 JVM de app y 9 instrumentadas SQLite, además de lint y compilación debug/release. La comprobación posterior de la rama `0.4.0` aprobó 24 JVM de app (8 de guardado, 8 de consulta y 8 de herramientas), 32 unitarias de dominio ejecutadas de nuevo y 10 instrumentadas en API 37; los JSON de consulta y guardado, lint debug/release y ambos APK fueron correctos. Dos regresiones de cancelación fallaron antes de corregir y aprobaron después. Las 13 advertencias visuales previas se conservan y datos no tiene incidencias. [Validación](VALIDACION.md) registra las comprobaciones y sus límites.

El APK 0.4 no se instaló sobre la aplicación oficial del usuario. La consulta visual, la rotación real y la aceptación funcional siguen pendientes. La consulta local no identifica usuarios: el aislamiento de reportes propios requiere futura autenticación; tampoco cambia estados ni completa el seguimiento administrativo del PDF.

Google Maps quedó acordado como próxima línea de trabajo. Nuestra parte recibe, valida y conserva `latitude` y `longitude` en `ReportLocation` y SQLite. El compañero desarrolla la vista del mapa, el pin y la confirmación de ubicación. Ambos coordinan la conexión con el contrato y la configuración compartida. El SDK, el proyecto de Google, la clave y la facturación siguen pendientes; no hay un mapa real en esta entrega ni captura GPS.

## Ciclo de cada función

1. Elegir una función pequeña y su resultado observable.
2. Identificar los archivos que toca y el contrato con la otra área.
3. Implementarla en una rama de tarea.
4. Ejecutar las pruebas necesarias y mostrar el resultado al usuario.
5. Ajustarla a su gusto; pasar a otra función después de revisar la actual.

Cada versión publicable actualiza la versión de la aplicación, el changelog, las fases y las notas en español de GitHub Releases, siguiendo [Releases](RELEASES.md). Se registran solo comprobaciones ejecutadas y se conserva la distinción entre autorización del trabajo y aceptación del resultado.

Usen ramas separadas, por ejemplo `feature/interfaz-inicial` y `feature/guardar-reporte`, y revisen los cambios antes de mezclarlos con `main`. El repositorio público permite consultar el código; escribir cambios requiere ser colaborador y aceptar la invitación.

## Trabajo anterior

La versión anterior y su respaldo se conservan en la PC como referencia. No se mezclan automáticamente todas sus funciones en esta base nueva. Las reglas de validación y lo aprendido sobre persistencia sirven como antecedente para este primer incremento.

## Criterio de la primera entrega

Un reporte válido produce un folio y queda guardado. Un reporte inválido devuelve errores por campo y no se inserta. Cerrar y reabrir la base conserva el reporte. El compañero puede conectar su interfaz sin depender de detalles de SQLite.
