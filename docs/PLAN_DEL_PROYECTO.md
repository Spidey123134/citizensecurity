# Plan del proyecto

Este plan resume los requisitos del **Documento de Visión Inicial: Desarrollo de una Aplicación Móvil para el Reporte de Emergencias y Robos con Geolocalización**, de septiembre de 2026, y los contrasta con CITIZENSECURITY. Se revisaron sus tres páginas. El PDF original y los documentos del respaldo permanecen fuera del repositorio; aquí no se incluyen datos personales.

La actualización activa es **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada para la **Release oficial solicitada expresamente por el usuario**. Se cerró el endurecimiento de evidencia GPS de 0.3.11 y se completaron dos pasos técnicos: [fase 6](FASE_6.md), conexión entre permiso, captura explícita y ciclo de vida; y [fase 7](FASE_7.md), borrador editable con ida/vuelta del mapa y recuperación de campos. La revisión de GitHub, incorporación de la interfaz del compañero y publicación de esta actualización están autorizadas; sus comprobaciones están registradas en [Validación](VALIDACION.md), y su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). El desarrollo posterior conserva el requisito de instrucciones precisas para las acciones externas.

La regla de **5 km alrededor del teléfono** se mantiene. [Proximidad](PROXIMIDAD_REPORTES.md) conserva la escritura protegida, y [Avance](AVANCE.md) mantiene **7/28 hitos, 25% estimado** del producto completo: los dos pasos técnicos no completan automáticamente dos funcionalidades del PDF. Siguen pendientes clave de Maps, GPS físico, mapa renderizado, controles conectados y aceptación del usuario. El borrador conserva campos y coordenadas como datos de entrada; no conserva permiso ni evidencia GPS como autorización y no abre SQLite, guarda reportes o activa una consulta. Las [mejoras posteriores](MEJORAS_FUTURAS.md) siguen como propuestas independientes.

La revisión remota anterior detectó [`b665a56`, «cambiar interfaz»](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), del compañero: rediseño de herramientas, ajustes de login, layout de crear cuenta y configuración del Daemon JVM. Ese commit no cambia lógica de reportes ni contratos. Su acoplamiento se incorporó mediante el merge local `433b3fb`: inicio y crear cuenta coinciden con el remoto, y herramientas conserva su rediseño e IDs más el ejemplo ficticio. El daemon quedó coordinado a JDK 27 mediante Gradle, sin las URLs anteriores de JVM 25. El layout de crear cuenta por sí solo no implementa cuentas ni suma otro hito de autenticación.

La página 1 contiene la portada, el resumen ejecutivo y los puntos clave. La página 2 presenta las secciones 1, 2, 3 y el inicio de 4. La página 3 completa las funcionalidades 4.2 a 4.7 y la sección 5. La visión describe el producto académico deseado; no fija versiones, fases técnicas ni proveedores.

## 1. El Problema

El documento identifica información dispersa, dificultad para ubicar un incidente, falta de seguimiento y evidencia separada del reporte. Recibir, validar, guardar y consultar un reporte local permite avanzar sobre la organización y la conservación de los datos. No resuelve por sí solo la comunicación con autoridades ni la atención del incidente.

## 2. La Solución Deseada

La propuesta permite registrar y organizar incidentes, conservar su información y consultar los reportes realizados. Incluye emergencia, robo, accidente, incendio, situación de riesgo y otros incidentes.

La fase 4 se apoya en esta sección y en la funcionalidad 4.4: consulta local por folio y estado, reutilizando el almacenamiento existente. Cerrar y reabrir SQLite para comprobar la permanencia es un criterio técnico de nuestra entrega, derivado de la necesidad de almacenar y consultar; el PDF no prescribe ese procedimiento.

El alcance sigue siendo académico y de simulación. Guardar o consultar localmente no envía unidades policiales, médicas o de protección civil.

## 3. Usuarios y Público Objetivo

La visión contempla ciudadanos, usuarios registrados, personal administrativo y estudiantes o desarrolladores. La base actual no tiene autenticación, propietario del reporte ni particiones por usuario. Por tanto, la consulta local todavía no acredita que cada usuario vea únicamente sus reportes.

El compañero desarrolla interfaces, navegación y autenticación. A petición del usuario, la base oficial 0.2.1 añadió un botón de herramientas y el acceso local admin / admin para demostrar recepción y validación; no implementa cuentas ni roles. La lógica y los datos usan contratos compartidos para que pueda conectar después sus pantallas. La autenticación, los permisos y los servicios externos necesitan su propio alcance y comprobaciones antes de afirmar que los distintos perfiles están implementados.

## 4. Funcionalidades Principales

| Requisito del PDF | Situación actual y trabajo acotado | Pendiente |
| --- | --- | --- |
| 4.1. Registro de usuarios, página 2 | El compañero aportó splash y login visual. Se conserva su interfaz y el acceso local admin / admin solicitado para demostración; no es autenticación real. | Cuenta, ingreso real, identificación del autor y aislamiento de datos. El login corresponde al compañero. |
| 4.2. Reporte de emergencia o robo, página 3 | El modelo recibe tipo, fecha y hora, descripción, ubicación y prioridad. 0.4.0a prepara el borrador editable con recuperación de campos e ida/vuelta del mapa; no inserta datos. La lógica de guardado se conserva y su acceso real queda oculto. El ejemplo visible utiliza datos ficticios fijos en SQLite en memoria. | Formulario del compañero, aceptación visual y autorización para activar guardado real. Fotografías y almacenamiento necesitan otro alcance. Fase 3 / 0.3.0 sigue sin entrega separada. |
| 4.3. Geolocalización, página 3 | Evidencia del teléfono separada del pin, precisa, vigente y no marcada como simulada, con permiso FINE y GPS habilitado al usarla. SDK, fuente de captura y puente de permisos implementados. El laboratorio comprueba integración Android mediante lecturas controladas y registro nativo cancelado antes del callback. 0.4.0a prepara el puente de ciclo de vida para conectar controles. | Lectura GPS física, controles conectados y comprobación del flujo en un teléfono. Una lectura controlada de laboratorio no acredita posición física. |
| 4.4. Consulta de reportes, página 3 | La lógica de 0.4 conserva consulta por folio, estado `REPORTED` y demostración técnica mediante Gradle. Su pulido queda Unreleased y toda consulta está oculta en la interfaz de la preparación 0.3.1. | Pantalla del compañero y autorización para activarla; reportes propios por usuario y seguimiento entre Reportado, En revisión, Atendido y Cerrado. El contrato no completa todo el requisito. |
| 4.5. Mapa de incidentes, página 3 | El PDF contempla un mapa por zonas con marcadores. El usuario eligió Google Maps y confirmación de puntos dentro de 5 km del teléfono. El SDK real está incorporado, con bindings de selección y modelo compartido; el borrador de 0.4.0a conserva los campos al volver del mapa. | Vista y controles del compañero, clave/configuración de Google Cloud, carga real y mapa por zonas con marcadores de incidentes. El PDF no elige Google Maps ni prescribe el radio de 5 km. |
| 4.6. Panel administrativo, página 3 | No hay administración ni cambio de estado en este incremento. | Consulta administrativa, clasificación, cambios de estado, permisos comprobados e historial. El alcance se acuerda antes de implementarlo. |
| 4.7. Notificaciones, página 3 | No se implementan avisos en este incremento. | Notificaciones por cambios de estado, configuración, permisos y pruebas del flujo. |

Los estados futuros descritos por 4.4 y los cambios administrativos de 4.6 son requisitos relacionados. Mostrar el estado inicial de un reporte no demuestra atención, cierre ni autorización administrativa.

## 5. Ventajas de la Delimitación

La visión busca una demostración académica con registro, organización y seguimiento, una arquitectura manejable y tareas repartidas. También contempla incorporar bases de datos, GPS, mapas, fotografías y notificaciones. Esa lista describe posibilidades del producto; no significa que todas estén activas o autorizadas en cada incremento.

Nuestra estructura de `app`, `core/domain` y `core/data` se conserva. El trabajo de ubicación y borrador reutiliza contratos existentes, sin una migración de SQLite, autenticación ni servicios en segundo plano. El ejemplo visible usa SQLite en memoria y datos ficticios fijos; no lee el formulario ni la base productiva. El compañero conserva sus pantallas y login, mientras nuestra parte entrega lógica, composición y pruebas.

## Secuencia y revisión

Para **0.4.0a**, el 6 de octubre el usuario autorizó expresamente revisar GitHub, subir la actualización y publicarla como Release oficial. Esta autorización cubre ese lote y la incorporación necesaria del trabajo del compañero; no permite publicar entregas futuras por haber terminado pruebas. [Versiones](RELEASES.md) registra el resultado de la publicación, y [Validación](VALIDACION.md) conserva la evidencia propia de cada corte. Los antecedentes siguientes no trasladan sus estados de publicación al lote actual.

El usuario pidió terminar **fase 4 / `0.4.0`** y explicarle los cambios. No autorizó integrarla en `main` ni publicarla. La [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0), código Android **7**, se publicó por una interpretación equivocada; la afirmación anterior de autorización era incorrecta. Por indicación posterior se conserva lo publicado y no se retira ni revierte.

La preparación **0.3.1 / código Android 8** queda como base local para la futura línea oficial. **0.3.2 / código Android 9 — Unreleased** preparó el contrato de selección; el usuario continúa ahora con **0.3.3 / código Android 10 — Unreleased** para impedir reportes lejanos, con un radio confirmado de 5 km. Se conserva la línea 0.3.x en lugar de la futura 0.5.0, sin renumerar publicaciones históricas. No se crea un commit, subida, integración, etiqueta ni Release. Sigue visible únicamente el ejemplo de guardado ficticio en memoria, además de recepción, validación y coordenadas. El guardado real, toda consulta 0.4 y la selección nueva permanecen sin acceso visual. El compañero conserva interfaz y login y puede preparar sus pantallas, pero no activar esos contratos hasta una instrucción precisa.

La nueva restricción exige evidencia del teléfono y revalida el punto antes de una escritura productiva. No bloquea un país: desde México rechaza un punto distante en Francia y desde una ubicación cercana en Francia aplica la misma regla. El filtro local no demuestra que ocurrió el incidente ni evita toda falsificación; captura GPS, permisos y servidor siguen pendientes. Este alcance procede de la petición posterior del usuario, no de un requisito de seguridad inventado para el PDF.

**0.3.0 y el pulido de 0.4.0 permanecen Unreleased**. Parte de su lógica ya está incluida en el código público y APK anteriores; no se afirma que esa fuente quede oculta o fuera de lo publicado. La [guía de fase 4](FASE_4.md) conserva los criterios técnicos; [Fases](FASES.md) distingue implementación, pruebas y revisión. Las tareas Gradle son comprobaciones de bases de prueba, no rutas visibles de la aplicación.

La entrega anterior [v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1) se conserva como historial. Terminar, probar o pulir no autoriza crear commits, subir, integrar, etiquetar, publicar ni borrar: cada acción requiere una instrucción humana precisa. Las pruebas técnicas y la publicación previa no registran aceptación funcional.

La ruta del respaldo es una referencia histórica: su entrega 4 era administración y describía otra integración en `appmovile`, con pantallas, fotografías e historial propios. Esa numeración y arquitectura no se trasladan a las fases actuales ni justifican copiar el proyecto completo. La asignación actual de interfaces y login al compañero prevalece sobre el reparto antiguo.

Las pruebas y límites se registran en [Validación](VALIDACION.md); los cambios, en el [Changelog](../CHANGELOG.md). La base 0.3.1 tiene comprobación real en el emulador del ejemplo y las herramientas anteriores, sin cambios en las bases productivas. El primer paso 0.3.2 aprobó el contrato de selección, 92 pruebas y lint/compilación debug; conserva las 13 advertencias visuales y no se instaló sobre la app 0.3.1 del emulador. Esa evidencia es histórica y no sustituye la comprobación de 0.3.3. [Avance](AVANCE.md) mantiene 25% estimado por hitos, con mapa real pendiente. La consulta de 0.4 sigue sin pantalla y oculta; su futuro flujo visual corresponde al compañero y queda pendiente de autorización para activarlo.
