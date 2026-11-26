# Exponer CitizenSecurity 0.7.1

## Recorrido de un minuto

1. Abre **CitizenSecurity** y toca **Acceso académico sin cuenta**. El grupo puede ver la app con el espacio académico del teléfono.
2. En **Inicio**, abre **Reportar un incidente**. Muestra cómo se elige la categoría, se describe lo ocurrido y se marca el lugar. El guardado de un reporte exige ubicación precisa y un punto cercano al teléfono. Para exponer en un emulador, puedes mostrar el formulario y la selección del lugar sin intentar registrar un incidente real.
3. Vuelve a **Inicio** y abre **Reportes guardados**. Un espacio nuevo empieza con la lista vacía. En **Mapa de reportes** puedes mostrar cómo se consultan las ubicaciones guardadas. La cartografía necesita conexión.
4. Abre **Ayuda y contactos** para enseñar el acceso a contactos de confianza. **Ayuda inmediata** abre el marcador de Android cuando eliges un número. En clase basta con mostrar los botones.

> CitizenSecurity organiza la información de un incidente para consultarla después en el mismo teléfono. En esta versión construimos la interfaz con Jetpack Compose y definimos en Kotlin cómo se conectan las pantallas. Al volver de Reportes a Inicio, la app conserva el espacio académico o la cuenta con la que entramos.

## Cómo explicarlo en código

- `MainActivity.kt` inicia el tema y la interfaz con `setContent`.
- `CitizenSecurityNavigation.kt` define el `NavHost` con las rutas y gestiona Volver. Los destinos con cámara, mapa y otros componentes Android conservan sus controladores y se abren desde el grafo.
- `account/AccountScreens.kt` y `release/HomeScreen.kt` contienen las pantallas de acceso, registro e inicio. `core:domain` aplica las reglas y `core:data` guarda reportes y cuentas en el dispositivo.
- La interfaz y el grafo se escriben en Kotlin. El manifiesto Android y los recursos del sistema siguen en XML.

## Abrir en Android Studio

Abre la carpeta que contiene `settings.gradle.kts`, elige JDK 25 para Gradle y ten instalado el SDK 37 con Build Tools 37.0.0. Selecciona la configuración `app` y ejecuta en un emulador Android 8 o posterior con OpenGL ES 3. Para compilar desde PowerShell ejecuta `.\gradlew.bat :app:assembleDebug`.

La APK de exposición se firma con la clave de depuración y está lista para una instalación nueva. Actualizar sobre la 0.7.0 publicada requiere el certificado académico de esa versión.
