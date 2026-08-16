import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

abstract class GenerateSanitizedComposeResourcesTask : DefaultTask() {
    @get:InputDirectory abstract val resourcesDirectory: DirectoryProperty

    @get:InputFile abstract val inputFile: RegularFileProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile
        val outputFile = outputRoot.resolve("files/mobile_devices.json")
        val sourceData = JsonSlurper().parse(inputFile.get().asFile)

        outputRoot.deleteRecursively()
        resourcesDirectory.get().asFile.copyRecursively(outputRoot, overwrite = true)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(sourceData.withoutSources())) + "\n"
        )
    }

    private fun Any?.withoutSources(): Any? =
        when (this) {
            is Map<*, *> ->
                entries
                    .filterNot { (key, _) -> key == "sources" }
                    .associate { (key, value) -> key.toString() to value.withoutSources() }
            is Iterable<*> -> map { it.withoutSources() }
            else -> this
        }
}

val generateSanitizedComposeResources =
    tasks.register<GenerateSanitizedComposeResourcesTask>("generateSanitizedComposeResources") {
        resourcesDirectory.set(layout.projectDirectory.dir("src/commonMain/composeResources"))
        inputFile.set(
            layout.projectDirectory.file(
                "src/commonMain/composeResources/files/mobile_devices.json"
            )
        )
        outputDirectory.set(layout.buildDirectory.dir("generated/composeResources/commonMain"))
    }

kotlin {
    android {
        namespace = "com.kormax.felicatool.shared"
        compileSdk = 37
        minSdk = 31
        withHostTestBuilder {}

        androidResources { enable = true }

        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    }

    compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi") }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.components.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.kormax.felicatool.shared.resources"
    generateResClass = always
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = generateSanitizedComposeResources.flatMap { it.outputDirectory },
    )
}
