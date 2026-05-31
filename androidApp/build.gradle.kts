import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.Node

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class GenerateServiceIconVectorDrawablesTask : DefaultTask() {
    @get:InputDirectory abstract val inputDir: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val sourceDir = inputDir.get().asFile
        val destinationRoot = outputDir.get().asFile
        val drawableDir = destinationRoot.resolve("drawable")
        destinationRoot.deleteRecursively()
        drawableDir.mkdirs()

        val documentBuilderFactory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }

        sourceDir
            .listFiles { file -> file.isFile && file.extension.equals("svg", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { svgFile ->
                val document = documentBuilderFactory.newDocumentBuilder().parse(svgFile)
                val svg = document.documentElement
                require(svg.localName == "svg") { "Expected SVG root in ${svgFile.path}" }

                val viewBox = svg.requiredAttribute("viewBox").parseViewBox(svgFile.name)
                val width = svg.requiredAttribute("width").toDpSize()
                val height = svg.requiredAttribute("height").toDpSize()
                val clipPaths = svg.collectClipPaths(svgFile.name)

                val vectorXml = buildString {
                    appendLine(
                        """<vector xmlns:android="http://schemas.android.com/apk/res/android""""
                    )
                    appendLine("""    android:width="${width}dp"""")
                    appendLine("""    android:height="${height}dp"""")
                    appendLine("""    android:viewportWidth="${viewBox.width}"""")
                    appendLine("""    android:viewportHeight="${viewBox.height}">""")
                    svg.appendVectorChildren(
                        builder = this,
                        indent = "  ",
                        clipPaths = clipPaths,
                        sourceName = svgFile.name,
                    )
                    appendLine("</vector>")
                }

                drawableDir.resolve(svgFile.nameWithoutExtension + ".xml").writeText(vectorXml)
            }
    }

    private data class ViewBox(val width: String, val height: String)

    private fun String.parseViewBox(sourceName: String): ViewBox {
        val parts = trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        require(parts.size == 4) { "Unsupported viewBox '$this' in $sourceName" }
        return ViewBox(width = parts[2].normalizeNumber(), height = parts[3].normalizeNumber())
    }

    private fun String.toDpSize(): String = removeSuffix("px").removeSuffix("dp").normalizeNumber()

    private fun String.normalizeNumber(): String =
        toDoubleOrNull()?.let { number ->
            if (number % 1.0 == 0.0) {
                number.toInt().toString()
            } else {
                number.toString()
            }
        } ?: this

    private fun Element.collectClipPaths(sourceName: String): Map<String, String> {
        val clipPaths = mutableMapOf<String, String>()
        val nodes = getElementsByTagNameNS("*", "clipPath")
        for (index in 0 until nodes.length) {
            val clipPath = nodes.item(index) as Element
            val id = clipPath.requiredAttribute("id")
            val path = clipPath.childElements().singleOrNull { it.localName == "path" }
            require(path != null) { "Unsupported clipPath '$id' in $sourceName" }
            clipPaths[id] = path.requiredAttribute("d")
        }
        return clipPaths
    }

    private fun Element.appendVectorChildren(
        builder: StringBuilder,
        indent: String,
        clipPaths: Map<String, String>,
        sourceName: String,
    ) {
        childElements().forEach { child ->
            when (child.localName) {
                "defs" -> Unit
                "g" -> child.appendVectorGroup(builder, indent, clipPaths, sourceName)
                "path" -> child.appendVectorPath(builder, indent)
                else -> error("Unsupported SVG element <${child.localName}> in $sourceName")
            }
        }
    }

    private fun Element.appendVectorGroup(
        builder: StringBuilder,
        indent: String,
        clipPaths: Map<String, String>,
        sourceName: String,
    ) {
        val unsupportedAttributes = attributeNames() - setOf("clip-path")
        require(unsupportedAttributes.isEmpty()) {
            "Unsupported group attributes $unsupportedAttributes in $sourceName"
        }

        builder.appendLine("${indent}<group>")
        getAttribute("clip-path")
            .takeIf { it.isNotBlank() }
            ?.let { clipReference ->
                val clipId = clipReference.removePrefix("url(#").removeSuffix(")")
                val clipPathData =
                    requireNotNull(clipPaths[clipId]) {
                        "Unknown clip path '$clipReference' in $sourceName"
                    }
                builder.appendLine("${indent}  <clip-path")
                builder.appendLine(
                    """${indent}      android:pathData="${clipPathData.xmlEscaped()}"/>"""
                )
            }
        appendVectorChildren(builder, "$indent  ", clipPaths, sourceName)
        builder.appendLine("${indent}</group>")
    }

    private fun Element.appendVectorPath(builder: StringBuilder, indent: String) {
        val unsupportedAttributes =
            attributeNames() -
                setOf(
                    "d",
                    "fill",
                    "fill-opacity",
                    "fill-rule",
                    "stroke",
                    "stroke-linecap",
                    "stroke-width",
                )
        require(unsupportedAttributes.isEmpty()) {
            "Unsupported path attributes $unsupportedAttributes"
        }

        val attributes = buildList {
            add("android:pathData" to requiredAttribute("d"))
            getAttribute("fill")
                .takeIf { it.isNotBlank() && it != "none" }
                ?.let { add("android:fillColor" to it) }
            getAttribute("fill-opacity")
                .takeIf { it.isNotBlank() }
                ?.let { add("android:fillAlpha" to it) }
            getAttribute("fill-rule")
                .takeIf { it.isNotBlank() }
                ?.let { add("android:fillType" to it.toAndroidFillType()) }
            getAttribute("stroke")
                .takeIf { it.isNotBlank() && it != "none" }
                ?.let { add("android:strokeColor" to it) }
            getAttribute("stroke-width")
                .takeIf { it.isNotBlank() }
                ?.let { add("android:strokeWidth" to it.normalizeNumber()) }
            getAttribute("stroke-linecap")
                .takeIf { it.isNotBlank() }
                ?.let { add("android:strokeLineCap" to it) }
        }

        builder.appendLine("${indent}<path")
        attributes.forEachIndexed { index, (name, value) ->
            val suffix = if (index == attributes.lastIndex) "/>" else ""
            builder.appendLine("""$indent    $name="${value.xmlEscaped()}"$suffix""")
        }
    }

    private fun String.toAndroidFillType(): String =
        when (lowercase(Locale.US)) {
            "evenodd" -> "evenOdd"
            "nonzero" -> "nonZero"
            else -> error("Unsupported fill-rule '$this'")
        }

    private fun Element.requiredAttribute(name: String): String =
        getAttribute(name).also {
            require(it.isNotBlank()) { "Missing required SVG attribute '$name'" }
        }

    private fun Element.attributeNames(): Set<String> =
        (0 until attributes.length).map { attributes.item(it).nodeName }.toSet()

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }

    private fun String.xmlEscaped(): String =
        replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}

val generateServiceIconVectorDrawables =
    tasks.register<GenerateServiceIconVectorDrawablesTask>("generateServiceIconVectorDrawables") {
        inputDir.set(
            layout.projectDirectory.dir(
                "../shared/src/commonMain/composeResources/files/service-icons"
            )
        )
        outputDir.set(layout.buildDirectory.dir("generated/res/service-icon-vectors"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateServiceIconVectorDrawables,
            GenerateServiceIconVectorDrawablesTask::outputDir,
        )
    }
}

android {
    namespace = "com.kormax.felicatool"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kormax.felicatool"
        minSdk = 31
        targetSdk = 37
        versionCode = 24
        versionName = "0.24.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin { compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi") } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(project(":shared"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
