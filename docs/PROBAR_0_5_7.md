# Probar 0.5.7 en Android

Descarga **citizensecurity-0.5.7-academico.apk** de [Release v0.5.7](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.7). Código Android 27. Instálala sobre la anterior, sin desinstalar ni borrar datos. Conserva los datos y consulta antes de desinstalar si Android informa un conflicto de firma.

## Buscar reportes existentes

1. Abre **Desarrollador → Inicio → Reportes guardados**. La lista debe conservar tus folios anteriores. Si no hay reportes, se indica que el teléfono todavía no tiene datos; la app no añade ejemplos ficticios.
2. Escribe una palabra o frase de una descripción o referencia y pulsa **Buscar reportes**. Puedes usar mayúsculas o escribir sin acentos. La búsqueda encuentra ese texto en cualquiera de esos dos campos.
3. En **Desde** / **Hasta**, escribe dd/MM/aaaa o pulsa **Elegir**. Puedes dejar un límite vacío. Busca la fecha del incidente, aunque lo hayas guardado otro día. El día Hasta se incluye completo.
4. Elige tipo o estado local para combinarlo con la búsqueda. Comprueba el contador de reportes visibles y abre un detalle; al volver se conserva la búsqueda aplicada.
5. Cambia el texto o las fechas sin aplicar y gira la pantalla: los campos escritos y el filtro aplicado deben conservarse por separado. Pulsa Buscar reportes para aplicar lo nuevo.
6. Después de una búsqueda válida, escribe una fecha imposible, como 31/02/2026, o invierte Desde/Hasta. Al buscar se explica el error y siguen los resultados de la búsqueda válida anterior.
7. Pulsa **Limpiar búsqueda**. Se retiran texto, ambas fechas, tipo y estado. Si una combinación no tiene coincidencias, **Mostrar todos** hace lo mismo; esto no cambia los reportes.
8. Puedes girar, usar una fuente grande y desplazarte para alcanzar los controles. El calendario se abre únicamente al pulsar Elegir.

## Consulta de folio

La búsqueda por folio completo sigue disponible. Abrir y volver de un detalle cancela su lectura pendiente y permite continuar consultando; una respuesta anterior no debe sustituir otro folio. La consulta solo lee: no solicita GPS, guarda datos ni cambia estados. Actualizar lista conserva los filtros aplicados.

Los filtros nuevos de texto/fecha pertenecen a la lista. Abrir Ver en el mapa mantiene el mapa por zona anterior; no implica aplicar allí la misma búsqueda.

## Código para el compañero

Conserva tus cambios propios antes de ejecutar `git fetch origin`, `git switch main`, `git pull --ff-only origin main`. Sincroniza Gradle y selecciona la configuración/módulo **app** para ejecutar CitizenSecurity. **verification:maps** ejecuta el laboratorio técnico separado testOnly; la APK oficial no lo instala automáticamente. Para probar la app basta ejecutar app o instalar la APK de la Release.

Se mantiene login/loading y acceso académico; no es autenticación real. Los reportes quedan en este teléfono, sin envío a autoridades ni avisos de emergencia. La prueba manual del GPS ya registrada sigue vigente; buscar o consultar no la vuelve a exigir.

[Fase 20](FASE_20.md) · [Avance 53.6%](AVANCE.md) · [Validación](VALIDACION.md).

La búsqueda admite hasta 512 caracteres, las fechas 32 y el campo de folio 64. Un pegado mayor muestra el error y conserva el texto anterior; no recorta ni altera reportes guardados. El aviso de base vacía aparece junto al encabezado.
