# Probar las funciones de la entrega 0.4.0

Abre el proyecto actualizado desde `main`. La entrega usa versión **0.4.0**, código Android **7**, y la etiqueta [v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0). Se conserva el splash y el login visual del compañero. Las herramientas introducidas en [v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1) siguen disponibles; sus botones conservan el alcance de fases 1 y 2.

## Entrar

- Pulsa **Desarrollador · funciones 0.2** en el inicio.
- También puedes escribir usuario **admin**, contraseña **admin**, y pulsar **Iniciar Sesión**.

Ambas rutas abren las mismas herramientas de recepción, validación y coordenadas. Este acceso es una demostración local pública: no crea cuentas, sesiones de servidor ni permisos administrativos. La contraseña se borra del campo al entrar.

## Probar

1. Modifica categoría, prioridad, descripción, fecha del incidente, referencia escrita o coordenadas. La fecha usa ISO-8601 UTC, por ejemplo `2026-10-03T12:00:00Z`; al abrir se propone una fecha reciente.
2. **Ver datos recibidos** convierte la entrada a `NewReport` y muestra los datos. Una fecha o coordenada no convertible muestra error por campo; este botón no afirma que el borrador sea válido.
3. **Validar reporte** aplica `ReportValidator`. Un borrador correcto muestra confirmación; uno inválido indica los campos a corregir.
4. **Probar ubicaciones** demuestra un punto válido sin referencia, coordenadas incompletas y latitud fuera de rango. Compara después estos ejemplos con tus propias coordenadas.
5. **Volver al inicio** regresa al login.

Los datos y el resultado de estas herramientas se conservan al recrear la actividad por rotación. Sus acciones no insertan reportes, no consultan SQLite y no llaman servicios externos. **Fase 3 / 0.3.0 sigue en Unreleased**; el cierre de 0.4.0 no publica esa versión.

## Comprobar la consulta de 0.4.0

Con un emulador o teléfono conectado y el entorno Android configurado, ejecuta desde la raíz del proyecto:

```powershell
.\gradlew.bat :core:data:consultarReporte --console=plain
```

La demostración crea dos reportes de prueba en una base temporal, cierra y reabre SQLite y recupera sus datos por folio y su estado inicial `REPORTED`. Comprueba también el orden del listado, un folio inexistente y que consultar no modifica registros. Al terminar muestra el resultado y exporta `core/data/build/reports/fase4/<dispositivo>/consulta.json`, fuera de Git. No lee ni inserta reportes en la base productiva de la aplicación.

El compañero puede conectar su futuro control mediante `MainActivity.reportQueryViewModel.findByFolio(folioEscrito)` y observar los estados de lectura, resultado encontrado, inexistente, formato incorrecto o error. El contrato y las reglas de ciclo de vida están en [Fase 4](FASE_4.md). Esta entrega incluye la lógica y la demostración; la pantalla de consulta todavía corresponde al compañero. Instalar el APK no añade ese control.

El mapa real y la autenticación siguen pendientes. [Fases](FASES.md) y [Validación](VALIDACION.md) registran el estado y las comprobaciones.
