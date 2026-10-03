# Plan del proyecto

Este plan resume los requisitos del **Documento de Visión Inicial: Desarrollo de una Aplicación Móvil para el Reporte de Emergencias y Robos con Geolocalización**, de septiembre de 2026, y los contrasta con CITIZENSECURITY. Se revisaron sus tres páginas. El PDF original y los documentos del respaldo permanecen fuera del repositorio; aquí no se incluyen datos personales.

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
| 4.2. Reporte de emergencia o robo, página 3 | El modelo recibe tipo, fecha y hora, descripción, ubicación y prioridad. La lógica valida y guarda localmente; el puente de guardado permite conectar un futuro formulario. | Formulario y flujo visual del compañero; fotografías y su almacenamiento requieren otro alcance. La entrega formal de fase 3 permanece pendiente. |
| 4.3. Geolocalización, página 3 | El contrato admite referencia escrita o un par de coordenadas que se valida y conserva. | Captura de la ubicación del dispositivo, permisos y comprobación GPS. Admitir coordenadas no equivale a capturar GPS. |
| 4.4. Consulta de reportes, página 3 | La entrega `0.4.0` incorpora consulta local por folio, lectura de estado `REPORTED` y demostración de permanencia y listado mediante Gradle. | Pantalla del compañero, reportes propios por usuario y seguimiento entre Reportado, En revisión, Atendido y Cerrado. Esta consulta no completa todo el requisito. |
| 4.5. Mapa de incidentes, página 3 | El PDF contempla un mapa por zonas con marcadores. El usuario eligió después Google Maps y selección mediante pin y confirmación; la base ya conserva coordenadas. | Vista y controles del compañero; SDK, proyecto de Google, clave y facturación compartidos. El PDF no elige Google Maps como proveedor. |
| 4.6. Panel administrativo, página 3 | No hay administración ni cambio de estado en este incremento. | Consulta administrativa, clasificación, cambios de estado, permisos comprobados e historial. El alcance se acuerda antes de implementarlo. |
| 4.7. Notificaciones, página 3 | No se implementan avisos en este incremento. | Notificaciones por cambios de estado, configuración, permisos y pruebas del flujo. |

Los estados futuros descritos por 4.4 y los cambios administrativos de 4.6 son requisitos relacionados. Mostrar el estado inicial de un reporte no demuestra atención, cierre ni autorización administrativa.

## 5. Ventajas de la Delimitación

La visión busca una demostración académica con registro, organización y seguimiento, una arquitectura manejable y tareas repartidas. También contempla incorporar bases de datos, GPS, mapas, fotografías y notificaciones. Esa lista describe posibilidades del producto; no significa que todas estén activas o autorizadas en cada incremento.

Nuestra estructura de `app`, `core/domain` y `core/data` se conserva. La entrega 0.4 reutiliza la consulta y SQLite existentes, sin crear pantallas, servicios, capturas GPS ni una migración nueva.

## Secuencia y revisión

El usuario autorizó cerrar **fase 4 / `0.4.0`** e integrarla a `main` para la etiqueta [v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0), con código Android **7**. Su alcance es consultar localmente por folio y comprobar que el reporte y su estado se conservan. La [guía de fase 4](FASE_4.md) define contrato y criterios; [Fases](FASES.md) distingue implementación, pruebas y revisión del usuario. La demostración se ejecuta con `:core:data:consultarReporte`; la pantalla queda pendiente del compañero.

La entrega anterior de la línea 0.2 se conserva como [v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1), con herramientas de recepción y validación presentes también en 0.4. **`0.3.0` permanece en Unreleased**, con su código adelantado conservado. La entrega 0.4.0 no publica una versión 0.3.0 ni completa la autenticación o las pantallas. La publicación oficial y las pruebas técnicas no registran aceptación funcional del usuario.

La ruta del respaldo es una referencia histórica: su entrega 4 era administración y describía otra integración en `appmovile`, con pantallas, fotografías e historial propios. Esa numeración y arquitectura no se trasladan a las fases actuales ni justifican copiar el proyecto completo. La asignación actual de interfaces y login al compañero prevalece sobre el reparto antiguo.

Las pruebas y límites se registran en [Validación](VALIDACION.md); los cambios, en el [Changelog](../CHANGELOG.md). Se mantienen 13 advertencias visuales previas. Las herramientas oficiales tuvieron comprobación visual; la consulta de 0.4 no tiene todavía una pantalla, y su flujo visual queda pendiente del trabajo del compañero.
