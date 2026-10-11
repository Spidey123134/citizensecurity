# Fase 19 — inicio funcional · 0.5.6

La pantalla verde de Desarrollador se convierte en **Inicio** por petición del usuario. Código Android 26. Mejora el recorrido de 4.2 Reportes, 4.4 Consulta y 4.5 Mapas sin crear criterios nuevos.

## Recorrido real

- **Reportar** abre el formulario real: datos, fotografía opcional, selección del punto y GPS, revisión, copia del borrador y guardado con folio. Conserva el radio de 5 km y el aviso de 200 m y ±2 h del mismo tipo.
- **Reportes guardados** abre lista/búsqueda/filtros y detalle de folios almacenados en este teléfono.
- **Mapa de reportes** abre el mapa con calles y consulta de reportes locales por zona.

Se retiran únicamente los controles visuales de recepción/validación ficticia, coordenadas predefinidas y guardado de ejemplo. Sus clases y pruebas históricas se conservan. Login, loading, acceso académico y enlaces existentes no se sustituyen. No se presenta el acceso académico como una cuenta real ni un rol administrativo.

## Recuperación y límites

ReportHomeViewModel lee los diarios privados reales de solicitud y copia del borrador. Distingue intento por comprobar, borrador recuperable y copia vinculada a un folio. Un intento puede estar insertado aunque aún falte comprobar el resultado; el inicio no afirma lo contrario.

Cada lectura puede fallar sin descartar la información que sí se pudo leer. Un error se muestra como desconocido y permite volver a comprobar. Actualizar usa generaciones y cancelación para impedir que una respuesta anterior sustituya la reciente. Entrar, girar o regresar no escribe diarios, abre SQLite, obtiene GPS ni restaura campos. El formulario vuelve a leer/verificar antes de recuperar o actuar; los indicadores no son una autorización ni un snapshot atómico entre archivos.

Se corrigió el área visible del desplazamiento: los insets se aplican al contenedor exterior, evitando botones parcialmente ocultos bajo la navegación al llevarlos a pantalla.

Los botones son desplazables, crecen con el texto y mantienen su área táctil. Los encabezados son accesibles y el cambio de estado puede anunciarse. Los reportes siguen locales; no se envían a autoridades ni avisan a emergencias.

## GPS y avance del PDF

**15/28 = 53.6%** con los mismos hitos/pesos. El 11 de octubre de 2026 el usuario confirmó que el GPS localizó el punto, aceptó la ubicación dentro del radio y mostró el pin. Esta aceptación manual completa el criterio 4.3 de recorrido GPS probado, que no exige guardar un folio. No se especificó versión de APK ni se aportaron logs, nuevas medidas de precisión o coordenadas. El usuario aclaró que solo confirmó el punto; no se atribuye un guardado físico nuevo.

Las comprobaciones automáticas y el GPS físico previo se registran separadamente. Guardar y abrir un folio en el teléfono sería una prueba transversal adicional 4.2/4.4. Siguen pendientes cuentas reales/aislamiento por autor, seguimiento administrativo y notificaciones. [Avance](AVANCE.md) conserva el cálculo; [Validación](VALIDACION.md) registra las ejecuciones.

## Comprobación

Las pruebas JVM cubren estados reales/errores parciales, lecturas concurrentes, reentrancia, cancelación, generaciones, retorno y cierre del modelo. Las nativas usan SQLite y diarios reales aislados para rutas/regreso, intento durable con identidad original, recuperación explícita, archivo dañado conservado y paisaje con fuente grande. Navegar y regresar deben conservar bytes y filas y no iniciar GPS. La fuente controlada pertenece solo a androidTest; no acredita una medición física adicional.

[Instalar y probar](PROBAR_0_5_6.md). El cierre con conteos, compilación, lint y evidencia se registra en [Validación](VALIDACION.md).

Cierre: **856 casos distintos aprobados, 25 nuevos; debug/release compilan, lint sin errores y 22 advertencias por variante de app**. Detalle y límites en [Validación](VALIDACION.md).
