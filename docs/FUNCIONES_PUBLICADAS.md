# Probar las funciones publicadas de 0.2

Abre el proyecto actualizado desde `main` o instala **citizensecurity-0.2.1-debug.apk** de [Release v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1). El APK es debug, firmado para pruebas académicas. Se conserva el splash y el login visual del compañero.

## Entrar

- Pulsa **Desarrollador · funciones 0.2** en el inicio.
- También puedes escribir usuario **admin**, contraseña **admin**, y pulsar **Iniciar Sesión**.

Ambas rutas abren las mismas funciones publicadas. Este acceso es una demostración local pública: no crea cuentas, sesiones de servidor ni permisos administrativos. La contraseña se borra del campo al entrar.

## Probar

1. Modifica categoría, prioridad, descripción, fecha del incidente, referencia escrita o coordenadas. La fecha usa ISO-8601 UTC, por ejemplo `2026-10-03T12:00:00Z`; al abrir se propone una fecha reciente.
2. **Ver datos recibidos** convierte la entrada a `NewReport` y muestra los datos. Una fecha o coordenada no convertible muestra error por campo; este botón no afirma que el borrador sea válido.
3. **Validar reporte** aplica `ReportValidator`. Un borrador correcto muestra confirmación; uno inválido indica los campos a corregir.
4. **Probar ubicaciones** demuestra un punto válido sin referencia, coordenadas incompletas y latitud fuera de rango. Compara después estos ejemplos con tus propias coordenadas.
5. **Volver al inicio** regresa al login.

Los datos y el resultado se conservan al recrear la actividad por rotación. Estas acciones no insertan reportes, no consultan SQLite y no llaman servicios externos. **Fase 3 / 0.3.0 y fase 4 / 0.4.0 siguen en Unreleased**, con su código conservado para trabajo posterior.

El mapa real y la autenticación siguen pendientes. [Fases](FASES.md) y [Validación](VALIDACION.md) registran el estado y las comprobaciones.
