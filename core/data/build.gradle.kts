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
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

val instrumentedResults = layout.buildDirectory.dir("outputs/androidTest-results/connected/debug")
val savingEvidence = layout.buildDirectory.dir("reports/fase3")
val queryEvidence = layout.buildDirectory.dir("reports/fase4")

tasks.register("consultarReporte") {
    group = "verification"
    description = "Consulta folios y sus estados después de reabrir una base temporal Android."
    dependsOn("connectedDebugAndroidTest")
    inputs.dir(instrumentedResults)
    outputs.dir(queryEvidence)
    outputs.upToDateWhen { false }
    doLast {
        val testClass = "com.example.citizensecurity.data.SqliteReportQueryTest"
        val logName = "logcat-$testClass-consultarFoliosTrasReabrirConservaDatosYOrdenSinModificarRegistros.txt"
        val logs = instrumentedResults.get().asFile.walkTopDown()
            .filter { it.isFile && it.name == logName }.sortedBy { it.absolutePath }.toList()
        check(logs.isNotEmpty()) {
            "Falta la evidencia de consulta. Ejecuta la instrumentación completa en un dispositivo."
        }
        logs.forEach { log ->
            val json = log.useLines { lines ->
                lines.firstOrNull { it.contains("FASE4_CONSULTA=") }
                    ?.substringAfter("FASE4_CONSULTA=")
            }
            check(json != null) { "Falta el resultado de consulta en ${log.name}." }
            val evidence = groovy.json.JsonSlurper().parseText(json) as Map<*, *>
            val reports = evidence["found"] as List<*>
            val orderedFolios = evidence["orderedFolios"] as List<*>
            check(evidence["temporaryDatabase"] == true && evidence["reopened"] == true)
            check(evidence["emptyCount"] == 0 && evidence["missingFound"] == false)
            check(evidence["countBefore"] == 2 && evidence["countAfter"] == 2 && reports.size == 2)
            val foundFolios = reports.map { (it as Map<*, *>)["id"] }
            check(orderedFolios == foundFolios.sortedByDescending { it.toString() })
            val output = queryEvidence.get().asFile.resolve(log.parentFile.name)
            check(output.isDirectory || output.mkdirs()) { "No se pudo crear la carpeta de evidencia." }
            output.resolve("consulta.json").writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(evidence)), Charsets.UTF_8,
            )
            logger.lifecycle("Consulta comprobada en ${log.parentFile.name}, con base temporal reabierta.")
            reports.forEach { entry ->
                val report = entry as Map<*, *>
                logger.lifecycle("Folio: ${report["id"]}; estado: ${report["status"]}; tipo: ${report["type"]}")
            }
            logger.lifecycle("Folio inexistente: ${evidence["missingFolio"]}; resultado: sin reporte.")
            logger.lifecycle("Registros antes y después de consultar: ${evidence["countBefore"]} / ${evidence["countAfter"]}.")
            logger.lifecycle("Orden estable para fechas iguales: ${orderedFolios.joinToString(", ")}.")
            logger.lifecycle("Evidencia JSON: ${output.resolve("consulta.json").absolutePath}")
        }
    }
}

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
