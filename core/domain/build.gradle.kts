plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}

val examples = sourceSets.create("examples") {
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += sourceSets.named("main").get().output
}

tasks.register<JavaExec>("recibirReporte") {
    group = "verification"
    description = "Muestra la entrada manual de un reporte usando el contrato existente."
    classpath = examples.runtimeClasspath
    mainClass.set("com.example.citizensecurity.examples.RecibirReporteKt")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            providers.gradleProperty("tipo").getOrElse("RISK"),
            providers.gradleProperty("descripcion")
                .getOrElse("Hay una luminaria dañada frente al parque."),
            providers.gradleProperty("prioridad").getOrElse("MEDIUM"),
            providers.gradleProperty("fecha").getOrElse("2026-10-02T23:30:00Z"),
            providers.gradleProperty("ubicacion").getOrElse("Frente al parque central"),
        )
    })
}

tasks.register<JavaExec>("validarReporte") {
    group = "verification"
    description = "Valida un reporte manual con las reglas existentes, sin guardarlo."
    classpath = examples.runtimeClasspath
    mainClass.set("com.example.citizensecurity.examples.ValidarReporte")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            providers.gradleProperty("tipo").getOrElse("RISK"),
            providers.gradleProperty("descripcion")
                .getOrElse("Hay una luminaria dañada frente al parque."),
            providers.gradleProperty("prioridad").getOrElse("MEDIUM"),
            providers.gradleProperty("fecha").getOrElse(""),
            providers.gradleProperty("ubicacion").getOrElse("Frente al parque central"),
            providers.gradleProperty("latitud").getOrElse(""),
            providers.gradleProperty("longitud").getOrElse(""),
        )
    })
}

tasks.register<JavaExec>("validarUbicaciones") {
    group = "verification"
    description = "Muestra la validación de tres puntos de ubicación, sin guardarlos."
    classpath = examples.runtimeClasspath
    mainClass.set("com.example.citizensecurity.examples.ValidarUbicaciones")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.register<JavaExec>("seleccionarUbicacion") {
    group = "verification"
    description = "Demuestra selección, confirmación y cancelación de un punto sin guardarlo."
    classpath = examples.runtimeClasspath
    mainClass.set("com.example.citizensecurity.examples.SeleccionarUbicacion")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.register<JavaExec>("comprobarProximidad") {
    group = "verification"
    description = "Demuestra el límite de 5 km con ubicaciones ficticias, sin guardar reportes."
    classpath = examples.runtimeClasspath
    mainClass.set("com.example.citizensecurity.examples.ComprobarProximidad")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
