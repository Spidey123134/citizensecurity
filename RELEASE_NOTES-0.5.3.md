# Reporte, folio, consulta y mapa · 0.5.3

En Desarrollador ya están disponibles Nuevo reporte · guardado local, Reportes en este teléfono y Mapa de reportes por zona.

- Llena el reporte, confirma el punto con GPS preciso, revisa y guarda en SQLite; recibes un folio para copiar, consultar o abrir en el mapa.
- Lista y detalle completos, búsqueda por folio y filtros de tipo/estado local. Mapa con calles, pines guardados por zona, límite de resultados y pin→detalle.
- Diario privado conserva el mismo intento antes de insertar: recupera el resultado después de una interrupción y evita duplicados. Al rechazar GPS conserva los campos; las correcciones sobreviven al giro.
- Corregido el panel del mapa: mostrar resultados ya no cambia su proyección ni invalida la propia consulta.
- Mantiene permiso preciso, GPS reciente/no simulado y radio de 5 km al guardar. Login y versiones anteriores conservados.

594 casos distintos aprobados, 65 definiciones nuevas. Debug/release compilan y lint no tiene errores. La integración nueva se probó con SQLite y mapa nativos y lectura de teléfono controlada solo en pruebas; queda pendiente aceptar el recorrido con GPS físico.

Avance por los mismos hitos del PDF: 13/28 = 46.4%. Fotos, cuentas verificadas, seguimiento administrativo y avisos requieren implementación. Los reportes son locales, no se envían a autoridades ni generan una emergencia.

APK académico 0.5.3 / código 23, misma firma debug que las entregas anteriores: instalar encima sin desinstalar. SHA256SUMS acompaña el archivo.

Para actualizar el proyecto, conserva tus cambios, git fetch origin, git switch main y git pull --ff-only origin main. [Fase 15 y comprobación](https://github.com/Spidey123134/citizensecurity/blob/main/docs/FASE_15.md).
