# Conseguir y configurar la clave de Google Maps

**Guía histórica.** El usuario eligió MapLibre Native + OpenFreeMap en fase 10. La preparación actual no necesita clave, registro ni tarjeta; sigue [Mapa con MapLibre](MAPA_MAPLIBRE.md). El proyecto ya no lee este archivo de secretos. Los pasos siguientes describen la configuración anterior de Google, conservada como referencia y sin autorizar acciones de facturación.

## Contexto anterior

Guía para CITIZENSECURITY, revisada el 6 de octubre de 2026. El SDK ya está incluido en el proyecto. La clave todavía está pendiente; estos pasos los realiza el dueño de la cuenta de Google Cloud. No se ha creado una cuenta de facturación ni una clave desde este trabajo local.

## Antes de empezar: cuenta y costo

Necesitas una cuenta de Google y un proyecto de Google Cloud con facturación habilitada y método de pago válido. Google exige facturación para usar el SDK aun cuando el servicio elegido sea gratuito. Véanse [requisitos del SDK](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing) y [métodos de pago](https://docs.cloud.google.com/billing/docs/how-to/payment-methods).

La [tabla de precios actual](https://developers.google.com/maps/billing-and-pricing/pricing) clasifica **Maps SDK** como uso gratuito ilimitado. Esto corresponde al mapa nativo Android/iOS **sin Map ID**. Usar un Map ID activa el SKU Dynamic Maps, con otra tarifa; Places, Routes y otros servicios también tienen condiciones propias. El proyecto actual usa el mapa básico sin Map ID y tocar un punto no requiere activar esos servicios. Consulta la [distinción de SKU](https://developers.google.com/maps/billing-and-pricing/sku-details). Revisa los precios de nuevo antes de distribuir la app o añadir servicios.

## 1. Crear el proyecto

1. Abre [Google Cloud Console](https://console.cloud.google.com/) e inicia sesión con tu cuenta de Google.
2. En el selector de proyectos de arriba, elige **Nuevo proyecto**.
3. Pon un nombre reconocible, por ejemplo `CITIZENSECURITY`, crea el proyecto y selecciónalo.
4. En **Facturación / Billing**, vincula una cuenta de facturación. Si no tienes una, Google te pedirá crearla y registrar un método de pago. Si no quieres completar este paso, detente aquí y consulta la alternativa al final.

Referencia: [crear una cuenta de facturación de Cloud](https://docs.cloud.google.com/billing/docs/how-to/create-billing-account).

## 2. Activar el servicio correcto

En el proyecto seleccionado, entra a **APIs y servicios → Biblioteca**, busca **Maps SDK for Android**, abre su ficha y pulsa **Habilitar**. Para este proyecto no hace falta habilitar Places, Routes ni Geocoding. Google reúne los pasos de cuenta, servicio y clave en [obtener una clave del SDK Android](https://developers.google.com/maps/documentation/android-sdk/get-api-key).

## 3. Crear y restringir la clave

1. Ve a **APIs y servicios → Credenciales → Crear credenciales → Clave de API**.
2. Abre la clave para editarla; puedes llamarla `CITIZENSECURITY Android`.
3. En **Restricciones de aplicaciones**, selecciona **Aplicaciones de Android** y añade esta combinación:

   | Campo | Valor para esta PC |
   | --- | --- |
   | Nombre del paquete | `com.example.citizensecurity` |
   | Huella SHA-1 del certificado | `6E:BD:64:8E:46:80:0D:DE:B8:0A:2F:D9:09:E8:2A:1A:93:0B:BC:47` |

4. En **Restricciones de API**, selecciona **Restringir clave** y permite únicamente **Maps SDK for Android**.
5. Guarda los cambios y copia la clave para el archivo local del siguiente paso. La propagación de restricciones puede tardar; si falla de inmediato, vuelve a probar después y comprueba los campos.

El SHA-1 anterior corresponde a la firma **debug de esta PC**, verificada sobre el APK local. Es la huella de un certificado público, no la clave de API. El compañero debe obtener la huella de su PC con `:app:signingReport` y añadir otra combinación paquete/SHA-1. Una firma release futura necesita su propia combinación.

Las claves incluidas en un APK pueden extraerse. Mantener el archivo fuera de Git protege la fuente; las restricciones protegen el uso del servicio. Referencia: [seguridad de claves de Google Maps](https://developers.google.com/maps/api-security-best-practices).

## 4. Guardarla solo en esta PC

En la raíz del proyecto, junto a `settings.gradle.kts`, copia `secrets.properties.example` como **`secrets.properties`** si aún no existe. Si ya existe, conserva su contenido y completa solo `MAPS_API_KEY`. En Android Studio puedes hacerlo desde el árbol del proyecto para evitar que Windows agregue `.txt` al nombre.

Ruta exacta:

```text
C:\Proyectos\citizensecurity\secrets.properties
```

Dentro debe quedar esta línea, reemplazando el ejemplo por tu clave y sin comillas:

```properties
MAPS_API_KEY=TU_CLAVE_AQUI
```

El archivo está ignorado por Git. No lo subas, no pegues la clave en el chat y no la metas en el manifiesto a mano. Cada PC configura su archivo local. Si Android Studio tiene una variable de entorno `MAPS_API_KEY`, esa variable tiene prioridad sobre el archivo; comprueba esa configuración si el mapa parece usar otra clave.

## 5. Compilar y comprobar

1. En Android Studio pulsa **Sync Project with Gradle Files** y vuelve a compilar el proyecto. La clave se incorpora al APK al compilar; editar el archivo no modifica una app ya instalada.
2. Cuando esté conectada la pantalla del mapa del compañero, comprueba que carga el mapa, recibe un toque y permite confirmar/cancelar el punto mediante el flujo acordado.
3. El permiso de ubicación es independiente: hace falta para comprobar los 5 km alrededor del teléfono, pero no para generar la clave ni consultar incidentes locales por zona.

**Poner la clave no crea la pantalla del mapa.** La integración visual del compañero, la carga real del SDK y la prueba con GPS físico siguen pendientes. El comprobador del proyecto solo valida presencia de clave y disponibilidad local de Google Play Services, no autorización Cloud.

Si el mapa aparece vacío o falla su autorización, comprueba proyecto seleccionado, facturación, SDK habilitado, paquete, SHA-1 de la firma que realmente instalaste y API permitida. Revisa el diagnóstico de Maps en Logcat sin compartir claves ni registros que las contengan. No quites las restricciones para dejar una clave pública funcionando.

## Alternativa si la facturación te impide avanzar

[MapLibre Native](https://github.com/maplibre/maplibre-native) es una biblioteca gratuita y abierta que permite mapas Android con datos de OpenStreetMap. Habría que elegir un proveedor de mapas y revisar su licencia, atribución y límites; la biblioteca gratuita no implica que cualquier servidor de mapas sea ilimitado. Los servidores públicos de OpenStreetMap tienen una [política de uso](https://operations.osmfoundation.org/policies/tiles/).

Es una opción si no quieres usar facturación de Google. Cambiar el SDK y adaptar la vista requiere un trabajo separado con el compañero. De momento se conserva Google Maps, que es la opción elegida, y la consulta por zona puede desarrollarse sin la clave.

Véanse [integración técnica de Google Maps](GOOGLE_MAPS.md), [incidentes por zona](FASE_9.md) y [acoplamiento de la interfaz](ACOPLAR_INTERFAZ.md).
