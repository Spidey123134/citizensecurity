# CITIZENSECURITY 0.5.5 — revisar reportes parecidos

**Fase 18 visible:** antes de guardar un reporte, la app revisa si este teléfono conserva incidentes del mismo tipo a **200 metros y hasta 2 horas antes o después** de la hora indicada.

- **Aviso con folios:** revisa distancia, hora y referencia, abre el reporte anterior y vuelve conservando tu borrador.
- **Decisión del usuario:** cancela para corregir o elige **Es otro incidente, guardar**. La coincidencia no determina veracidad ni bloquea un incidente distinto.
- **Consulta limitada y fallos claros:** hasta cinco coincidencias entre 200 candidatos; una lectura incompleta o fallida requiere tu decisión.
- **Continuidad:** corregir o salir durante la consulta invalida su resultado; rotación conserva el aviso. El guardado sigue comprobando GPS preciso y radio de 5 km, foto y el UUID durable. Sin cambios de esquema SQLite ni pérdida de reportes previos.

Fotos, copia recuperable, consulta y mapa con calles siguen visibles. El login/splash del compañero se conserva. Los reportes permanecen en este teléfono; no se envían a autoridades.

**831 casos distintos aprobados, 79 definiciones nuevas.** Debug/release compilan y lint sin errores (23 advertencias por variante de app). El cierre está en [Validación](https://github.com/Spidey123134/citizensecurity/blob/main/docs/VALIDACION.md). Las pruebas de integración con fuente GPS controlada no acreditan aceptación física final.

**Avance del PDF: 14/28 = 50%.** Esta ampliación fortalece reportes/consulta; cuentas reales, seguimiento administrativo y notificaciones siguen pendientes.

**Instalar:** descarga `citizensecurity-0.5.5-academico.apk` en Assets e instala encima de la anterior, sin desinstalar ni borrar datos. Código 25 y misma firma académica. Incluye `SHA256SUMS-0.5.5.txt`.

[Pasos para probar](https://github.com/Spidey123134/citizensecurity/blob/main/docs/PROBAR_0_5_5.md) · [Fase 18](https://github.com/Spidey123134/citizensecurity/blob/main/docs/FASE_18.md).

Para el equipo: conserva tus cambios, `git fetch origin`, `git switch main`, `git pull --ff-only origin main`.
