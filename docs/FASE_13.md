# Fase 13 — permisos claros y recuperación del GPS

**Registro histórico de su corte local.** Este código de interfaz quedó incorporado a la entrega oficial [0.5.1](ENTREGA_0_5_1.md), por la instrucción posterior de trabajar con Releases. Los resultados y restricciones siguientes corresponden al corte original.

Estado: local, Unreleased, 9 de octubre de 2026. Continúa la vista previa de fase 12 por instrucción del usuario. Oficial 0.5.0 intacta; debug 0.5.0-fase13, código Android 20. Sin acciones Git de escritura o publicación.

## Cambios

LocationGuidance interpreta los estados existentes sin pedir permisos, iniciar GPS o modificar las decisiones de cercanía. Explica por qué ubicación aproximada no permite confirmar 5 km, cómo repetir un permiso cancelado o fallido y qué revisar manualmente si Android no abre el diálogo. shouldExplain=false no se presenta como una denegación permanente. La espera del permiso tiene prioridad sobre permisos previos y errores; un fallo de comprobación no se presenta como acceso preciso acreditado.

La pantalla explica que conceder permiso no inicia GPS. Ante timeout, recomienda ventana/espacio abierto y un nuevo gesto, conservando el punto. Ante ubicación desactivada, pide activarla manualmente y volver a pulsar Buscar mi ubicación. Los otros rechazos conservan la razón del modelo. Los mensajes cambiantes usan región accesible polite.

La zona de controles del mapa tiene altura limitada por LocalWindowInfo y desplazamiento vertical para conservar mapa y acceso a Cancelar en horizontal o ventanas pequeñas. No cambia el radio, precisión, antigüedad, rechazo de ubicación simulada, guardado, consulta, login o navegación de entrada. La Activity y los mensajes nuevos solo existen en debug.

## Validación y límites

Se añadieron 12 casos JVM para precedencia de estados y mensajes de recuperación, y un caso nativo que gira la Activity, comprueba mapa/mensajes/confirmación bloqueada, accede a Cancelar y conserva el texto. Los seis nativos anteriores se vuelven a ejecutar sin contarlos como definiciones nuevas. Los resultados finales se registran en VALIDACION.md y el recibo local ignorado cierre-fase13.json.

No hay teléfono conectado en esta sesión. La prueba nativa usa una instancia de emulador read-only; no acredita GPS físico ni elección aproximada/denegada en el diálogo real de esta pantalla. Los 12 casos de mensajes representan estados controlados, no permisos nuevos concedidos por Android. Las reglas y el puente de permisos mantienen su evidencia previa por separado. Faltan aceptación física, revisión del diseño y autorización expresa para activar datos reales o publicar.

## Probar

En el APK debug abre Desarrollador → Nuevo reporte · vista previa → Seleccionar punto en el mapa. Lee el estado de permiso y usa sus controles por gesto explícito. Si Android permite solo ubicación aproximada, selecciona Precisa al volver a pedir permiso; si no aparece el diálogo, revisa manualmente los permisos de esta app en Ajustes. Con permiso preciso, pulsa Buscar mi ubicación. Un timeout conserva el borrador y permite reintentar. En horizontal, desplaza los controles para llegar a Cancelar o Confirmar.

Avance acreditado del plan: 10/28 = 35.7%. Este incremento pule la integración visual de reporte/geolocalización/mapas y no declara completado un hito sin aceptación física.

## Cierre local

285 JVM y siete nativos aprobados: 292 casos de este corte, 13 definiciones nuevas. Debug/release/pruebas compilan; lint sin errores con 19 advertencias anteriores por variante y firma debug v2 válida. La captura real muestra calles, etiquetas, pin, mensaje de permiso denegado y confirmación bloqueada sin GPS. El caso horizontal comprobó acceso a Cancelar y retención del texto. No se acredita GPS físico, selección aproximada en el diálogo real o aceptación completa. Oficial 0.5.0 y APK de fase 12 preservados; no hay commit ni publicación.

Evidencia ignorada en .gradle/validacion: fase13-final-build.log, fase13-ui-final.log, fase13-mapa.png, fase13-formulario.png, citizensecurity-fase13-preview.apk y cierre-fase13.json. El siguiente paso pendiente es probar permiso preciso, captura y confirmación en un teléfono real antes de decidir la activación del guardado. El borrador sigue sin guardar ni enviar reportes.
