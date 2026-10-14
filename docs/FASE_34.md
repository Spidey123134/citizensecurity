# Fase 34 — Ayuda en Ajustes rápidos

**Implementación validada e incluida en 0.6.1 · código Android 29 · 14 de octubre de 2026.** El usuario pidió cerrar la cámara, avanzar otra fase y publicar una APK/Release **0.6.1**. Esta fase implementa **E04** de [Mejoras útiles](MEJORAS_UTILES.md): un mosaico en Ajustes rápidos para abrir **Ayuda inmediata**. La publicación de esta entrega está autorizada expresamente; resultados y verificaciones del cierre en [Validación](VALIDACION.md).

## Recorrido útil

El usuario elige añadir el mosaico **Ayuda** al panel de Ajustes rápidos. Tocarlo abre la pantalla pública **Ayuda inmediata**, sin exigir login o GPS. Allí se muestran los accesos al marcador para **911/089**; una segunda decisión abre `ACTION_DIAL` y el usuario decide llamar en el teléfono. Tocar el mosaico, añadirlo o desplegar el panel no realiza llamadas.

El mosaico no contiene cuenta, folio, ubicación o números privados, ni consulta reportes/contactos o restaura una sesión. Abrir Ayuda conserva la identidad en memoria cuando existe; no concede permisos administrativos. Añadir o quitar el mosaico no cambia el SOS o los botones físicos del sistema.

Cuando el teléfono está bloqueado se solicita el desbloqueo antes de abrir la pantalla. Cancelarlo no debe abrir Ayuda ni el marcador. El servicio actúa por un toque vigente; vincularlo, escuchar cambios del panel o recrearlo no inicia una navegación. Este acceso usa el mecanismo de [TileService](https://developer.android.com/reference/android/service/quicksettings/TileService), incluido `unlockAndRun` para la comprobación de desbloqueo.

## Alta voluntaria y compatibilidad

La opción de Ajustes rápidos desde Ayuda solicita el alta por un gesto explícito. En **API 33 o posterior**, `StatusBarManager.requestAddTileService` presenta al usuario la petición del sistema. La app debe estar en primer plano; Android puede rechazarla y el usuario puede cancelar. Pedir el alta no equivale a confirmar que el mosaico se añadió. [Referencia oficial de StatusBarManager](https://developer.android.com/reference/android/app/StatusBarManager#requestAddTileService(android.content.ComponentName,%20java.lang.CharSequence,%20android.graphics.drawable.Icon,%20java.util.concurrent.Executor,%20java.util.function.Consumer%3Cjava.lang.Integer%3E)).

En versiones anteriores compatibles del proyecto (**API 26–32**) se explica el alta manual: abrir el panel, editar los mosaicos y arrastrar **Ayuda** a los activos. También es una alternativa en versiones recientes cuando la petición no está disponible o se rechaza. La posición, icono para editar y texto del editor dependen del fabricante. El usuario puede retirar el mosaico desde ese mismo editor. [Guía oficial de mosaicos Android](https://developer.android.com/develop/ui/views/quicksettings-tiles).

Al abrir la pantalla desde el servicio se usa **PendingIntent en API 34 o posterior** y el overload con **Intent en API 26–33**. La referencia de TileService indica que la variante con Intent no debe usarse desde API 34. Ambas rutas tienen el mismo destino público y no incluyen datos privados. La compatibilidad declarada/compilada de esas ramas no acredita ejecución física en todas esas versiones.

## Comprobación y límites del corte

La validación específica de Ajustes rápidos aprobó **33 definiciones nuevas: 28 JVM y cinco Android**, sobre controlador/servicio, petición de alta y contratos públicos. El cierre agrupado **0.6.1 aprobó 1237 casos distintos, 307 definiciones nuevas frente a la APK 0.6.0**: 1074 JVM y 163 Android API 37, sin fallos/errores/omitidos finales. Lint app cero errores/22 avisos por variante y datos sin incidencias. Recibos y límites en [Validación](VALIDACION.md).

Las pruebas del servicio/protocolo se distinguen de la aceptación del panel de un fabricante: un callback simulado no demuestra que Android añadió el mosaico. La ruta de desbloqueo/cancelación y la retirada se comprueban con sus contratos; no se atribuye una aceptación física del panel, ejecución física API 26 o muerte completa del proceso. Las comprobaciones Android del cierre se ejecutaron en API 37.

Además se comprobó la **integración con SystemUI en el emulador API 37**, asistida por shell: añadir el mosaico propio, desplegar Ajustes rápidos y tocarlo desde Inicio del teléfono enlazó `EmergencyHelpTileService` y abrió `EmergencyAccessActivity` con Ayuda/911/089. Después se retiró únicamente ese mosaico y se restauró exactamente la lista anterior de Ajustes rápidos. Esta comprobación está fuera de los 1237 casos automatizados; no equivale a un alta manual, desbloqueo físico o aceptación de otro fabricante.

El corte previo de cámara está aprobado con **794 casos distintos, 71 nuevos**: 766 JVM de app y 28 Android (15 almacén/provider, 7 cámara y 6 regresiones de galería), sin fallos/errores/omitidos finales. La captura utiliza imágenes controladas sobre el provider real; no sustituye la aceptación con la cámara física. Ese corte no se suma otra vez a los totales nuevos de esta entrega.

La APK **0.6.1 / código 29** tiene paquete/versión y el mismo certificado académico verificados; conserva los esquemas y permite instalar encima de la anterior sin desinstalar/borrar datos. **55.217.523 bytes**, SHA-256 `7395a049aee8cf92324ac31f3c4e08453fb639bcf5103fc8eaa48484302815f1`. No se declara una prueba de instalación como actualización que no se ejecutó en este corte. [Entrega](ENTREGA_0_6_1.md) · [Pasos para probar](PROBAR_0_6_1.md).

**19/28 = 67.9% permanece**. Cámara y acceso rápido amplían utilidad; no completan los nueve criterios pendientes de seguimiento, administración y notificaciones. La asignación administrativa sigue pendiente por elección del usuario y `admin/admin` conserva acceso académico. Login/loading del compañero y datos históricos se conservan.
