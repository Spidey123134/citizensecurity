# Empezar a trabajar en el repositorio

Repositorio compartido: [Spidey123134/citizensecurity](https://github.com/Spidey123134/citizensecurity). `main` contiene la base que usa el equipo; cada función se desarrolla en una rama propia y se integra mediante revisión de cambios. La entrega actual es **0.4.0**, código Android **7**, con etiqueta [v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0). Para trabajar juntos se actualiza desde `main`; las Releases conservan las entregas por versión y no sustituyen ese flujo.

## Acceso y primera copia

El acceso de escritura de `vazdavr-sudo` se comprobó el 2 de octubre de 2026. Puede subir sus ramas usando esa cuenta. El repositorio público permite leer y clonar el código.

Para crear una copia nueva:

```powershell
git clone https://github.com/Spidey123134/citizensecurity.git
cd citizensecurity
git switch -c feature/interfaz-inicial
```

Si ya tienes una copia, comprueba `git status` y guarda tus cambios en tu rama antes de actualizar. Con el directorio de trabajo limpio:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/interfaz-inicial
```

Usa un nombre de rama nuevo si `feature/interfaz-inicial` ya existe; si estás continuando esa tarea, vuelve a su rama existente.

## Abrir y comprobar la base

1. Abre la carpeta `citizensecurity` completa en Android Studio.
2. Configura JDK 27 para Gradle e instala Android SDK Platform 37.0 y Build Tools 37.0.0.
3. Configura la ruta del SDK de tu PC cuando Android Studio lo solicite. `local.properties` y los ajustes locales del IDE quedan fuera de Git.
4. Sincroniza Gradle y pulsa **Run**. Se conservan `SplashActivity`, `activity_splash.xml` y el diseño visual de login del compañero. La entrega actual es `0.4.0`; `0.2.1` permanece como historial y la entrega de fase 3 / `0.3.0` continúa Unreleased, según [Versiones](RELEASES.md).

El botón **Desarrollador · funciones 0.2** y el acceso **admin / admin** siguen abriendo las herramientas de recepción, validación y coordenadas. La consulta nueva de 0.4 está en el contrato y en la demostración Gradle; todavía no hay una pantalla para consultarla. Véase [Funciones de la entrega](FUNCIONES_PUBLICADAS.md).

Para ejecutar los comandos desde una terminal de Windows, configura también `JAVA_HOME`. Sustituye la ruta del ejemplo por la de tu propio JDK 27; el ajuste del JDK dentro del IDE no configura esta variable de la terminal.

```powershell
$env:JAVA_HOME = 'C:\ruta\a\tu\jdk-27'
.\gradlew.bat :core:domain:recibirReporte --console=plain
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
```

El primer comando de Gradle muestra cómo el contrato recibe los datos del reporte. El segundo comprueba los modelos de la aplicación, las reglas existentes, lint y compilación. La consulta de 0.4 se demuestra con un emulador o teléfono conectado:

```powershell
.\gradlew.bat :core:data:consultarReporte --console=plain
```

La tarea ejecuta la suite instrumentada y muestra la recuperación por folio y el listado después de reabrir una base de prueba temporal. Exporta la evidencia a `core/data/build/reports/fase4/<dispositivo>/consulta.json`, fuera de Git. La prueba directa de persistencia usa `:core:data:connectedDebugAndroidTest`; las comprobaciones de cada entrega están en [Validación](VALIDACION.md).

La pantalla de carga tiene su recurso de layout desde el commit `e18e636`. La autenticación y la conexión visual del reporte siguen pendientes del compañero; las tareas de `core/domain` y `core/data` permiten comprobar nuestra parte.

## Dónde trabaja cada uno

| Área | Responsable | Punto de entrada |
| --- | --- | --- |
| Interfaces, menú y navegación | `vazdavr-sudo` | `app/src/main/kotlin/com/example/citizensecurity/MainActivity.kt`, `app/src/main/res/layout/` y recursos visuales |
| Login | `vazdavr-sudo` | Se define como su propia función antes de conectar cuentas o servicios |
| Reglas y modelos del reporte | Nuestra parte de lógica y datos | `core/domain/src/main/kotlin/com/example/citizensecurity/domain/` |
| Guardado y consultas SQLite | Nuestra parte de lógica y datos | `core/data/src/main/kotlin/com/example/citizensecurity/data/` |
| Dependencias, manifiesto y conexión entre capas | Ambos | Se coordinan al conectar la interfaz |

La futura interfaz construye un `NewReport` y usa `ReportViewModel` para guardar y presentar errores por campo. El control de consulta puede llamar a `MainActivity.reportQueryViewModel.findByFolio(folioEscrito)` y observar su estado. Ambos modelos utilizan el repositorio compartido de `CitizenSecurityApplication`; la pantalla no abre ni cierra SQLite. El ejemplo y los campos están en [Recibir reporte](RECIBIR_REPORTE.md); la conexión de guardado, en [Acoplar interfaz](ACOPLAR_INTERFAZ.md); la consulta, en [Fase 4](FASE_4.md).

## Subir una función

Antes de crear el commit, actualiza [Changelog](../CHANGELOG.md) y [Fases](FASES.md) con el estado real del incremento. Revisa qué archivos incluye el cambio:

```powershell
git status
git diff
git add app/src/main CHANGELOG.md docs/FASES.md
git diff --cached
git commit -m "Añadir interfaz inicial"
git push -u origin HEAD
```

El ejemplo añade los archivos de la interfaz y su documentación. Si tu función necesita otros archivos, inclúyelos de forma explícita y coordina las áreas compartidas. `HEAD` publica la rama actual, incluso si elegiste otro nombre. Abre un pull request hacia `main` y describe el comportamiento y las comprobaciones ejecutadas.

El trabajo sigue el ciclo de [Trabajo del equipo](TRABAJO_EQUIPO.md): una función por vez, prueba y revisión antes de escoger la siguiente.
