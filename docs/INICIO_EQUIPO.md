# Empezar a trabajar en el repositorio

Repositorio compartido: [Spidey123134/citizensecurity](https://github.com/Spidey123134/citizensecurity). `main` contiene la base que usa el equipo; cada función se desarrolla en una rama propia y se integra mediante revisión de cambios.

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
4. Sincroniza Gradle. La actividad inicial contiene un contenedor vacío para desarrollar las interfaces.

Para ejecutar los comandos desde una terminal de Windows, configura también `JAVA_HOME`. Sustituye la ruta del ejemplo por la de tu propio JDK 27; el ajuste del JDK dentro del IDE no configura esta variable de la terminal.

```powershell
$env:JAVA_HOME = 'C:\ruta\a\tu\jdk-27'
.\gradlew.bat :core:domain:recibirReporte --console=plain
.\gradlew.bat :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
```

El primer comando de Gradle muestra cómo el contrato recibe los datos del reporte. El segundo comprueba las reglas existentes, lint y compilación. La prueba de persistencia con `:core:data:connectedDebugAndroidTest` requiere un emulador o teléfono; sus resultados iniciales están en [Validación](VALIDACION.md).

## Dónde trabaja cada uno

| Área | Responsable | Punto de entrada |
| --- | --- | --- |
| Interfaces, menú y navegación | `vazdavr-sudo` | `app/src/main/kotlin/com/example/citizensecurity/MainActivity.kt`, `app/src/main/res/layout/` y recursos visuales |
| Login | `vazdavr-sudo` | Se define como su propia función antes de conectar cuentas o servicios |
| Reglas y modelos del reporte | Nuestra parte de lógica y datos | `core/domain/src/main/kotlin/com/example/citizensecurity/domain/` |
| Guardado y consultas SQLite | Nuestra parte de lógica y datos | `core/data/src/main/kotlin/com/example/citizensecurity/data/` |
| Dependencias, manifiesto y conexión entre capas | Ambos | Se coordinan al conectar la interfaz |

La interfaz construye un `NewReport` y utiliza el repositorio compartido de `CitizenSecurityApplication`. El ejemplo y los campos están en [Recibir reporte](RECIBIR_REPORTE.md); el guardado y los errores por campo están en [Contrato de guardado](GUARDAR_REPORTE.md).

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
