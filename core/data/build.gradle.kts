plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.citizensecurity.data"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.coroutines.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

val instrumentedResults = layout.buildDirectory.dir("outputs/androidTest-results/connected/debug")
val savingEvidence = layout.buildDirectory.dir("reports/fase3")

tasks.register("guardarReporte") {
    group = "verification"
    description = "Guarda y recupera un reporte de ejemplo en una base temporal Android."
    dependsOn("connectedDebugAndroidTest")
    inputs.dir(instrumentedResults)
    outputs.dir(savingEvidence)
    outputs.upToDateWhen { false }
    doLast {
        val testClass = "com.example.citizensecurity.data.SqliteReportRepositoryTest"
        val savedFile = "logcat-$testClass-coordenadasSinReferenciaYFechaPrecisaSeConservanAlReabrir.txt"
        val rejectedFile = "logcat-$testClass-reporteInvalidoNoModificaLosDatosGuardados.txt"
        val savedLogs = instrumentedResults.get().asFile.walkTopDown()
            .filter { it.isFile && it.name == savedFile }.sortedBy { it.absolutePath }.toList()
        check(savedLogs.isNotEmpty()) {
            "Falta la evidencia del guardado. Ejecuta la instrumentación completa en un dispositivo."
        }

        fun readEvidence(file: File, marker: String): Map<*, *> {
            check(file.isFile) { "Falta la evidencia de rechazo en ${file.parentFile.name}." }
            val json = file.useLines { lines ->
                lines.firstOrNull { it.contains("$marker=") }?.substringAfter("$marker=")
            }
            check(json != null) { "Falta el resultado $marker en ${file.name}." }
            return groovy.json.JsonSlurper().parseText(json) as Map<*, *>
        }

        savedLogs.forEach { log ->
            val saved = readEvidence(log, "FASE3_GUARDADO")
            val rejected = readEvidence(File(log.parentFile, rejectedFile), "FASE3_RECHAZO")
            check(saved["temporaryDatabase"] == true && saved["reopened"] == true &&
                saved["saved"] == saved["restored"])
            check(rejected["temporaryDatabase"] == true && rejected["rejected"] == true &&
                rejected["inserted"] == false && rejected["countBefore"] == rejected["countAfter"])

            val report = saved["saved"] as Map<*, *>
            val location = report["location"] as Map<*, *>
            val output = savingEvidence.get().asFile.resolve(log.parentFile.name)
            check(output.isDirectory || output.mkdirs()) { "No se pudo crear la carpeta de evidencia." }
            output.resolve("reporte.json").writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(saved)), Charsets.UTF_8,
            )
            output.resolve("rechazo.json").writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(rejected)), Charsets.UTF_8,
            )

            logger.lifecycle("Guardado comprobado en ${log.parentFile.name}, con base temporal.")
            logger.lifecycle("Folio: ${report["id"]}")
            logger.lifecycle("Tipo: ${report["type"]}; prioridad: ${report["priority"]}")
            logger.lifecycle("Descripción: ${report["description"]}")
            logger.lifecycle("Fecha del incidente (UTC): ${report["occurredAt"]}")
            logger.lifecycle("Fecha de guardado (UTC): ${report["createdAt"]}; estado: ${report["status"]}")
            logger.lifecycle("Punto: ${location["latitude"]}, ${location["longitude"]}")
            logger.lifecycle("Reapertura: se recuperó el mismo reporte por su folio.")
            logger.lifecycle("Rechazo: no se insertó otro reporte; registros conservados: ${rejected["countAfter"]}.")
            logger.lifecycle("Evidencia JSON: ${output.absolutePath}")
        }
    }
}
