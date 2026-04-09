/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2025-2026 LTFan (aka xfqwdsj)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import groovy.json.JsonSlurper
import java.net.URI
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.room)
}

android {
    val packageName = "top.ltfan.knowmad"

    namespace = packageName
    compileSdk = 37

    defaultConfig {
        applicationId = packageName
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
    }

    val keyStoreFile = file("key.jks")
    val keyPropertiesFile = file("key.properties")
    val keyProperties = Properties()

    if (keyStoreFile.exists() && keyPropertiesFile.exists()) {
        keyProperties.load(keyPropertiesFile.inputStream())
        signingConfigs {
            create("release") {
                storeFile = keyStoreFile
                storePassword = keyProperties["storePassword"] as String?
                keyAlias = keyProperties["keyAlias"] as String?
                keyPassword = keyProperties["keyPassword"] as String?
            }
        }
        buildTypes {
            release {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/DEPENDENCIES"

            // First found in djl
            excludes += "native/**"
            excludes += "com/sun/jna/**"
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        freeCompilerArgs.add("-XXLanguage:+UnnamedLocalVariables")
        optIn.add("kotlin.ExperimentalUnsignedTypes")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        optIn.add("com.google.accompanist.permissions.ExperimentalPermissionsApi")
        optIn.add("dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose))
    implementation(libs.bundles.all)
    ksp(libs.room.compiler)
    debugImplementation(libs.bundles.debug)
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.androidTest)
}

abstract class DownloadMathJaxTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputDir.convention(
            project.layout.buildDirectory.dir("generated/mathjax/${name}"),
        )

        val api = "https://data.jsdelivr.com/v1/packages/npm/mathjax/resolved"
        val apiUrl = URI(api).toURL()

        val metaString = try {
            println("Reading MathJax metadata from $api...")
            apiUrl.readText()
        } catch (e: Throwable) {
            println("Failed to read MathJax metadata: ${e.localizedMessage}")
            e.printStackTrace()
            ""
        }

        val meta = JsonSlurper().parseText(metaString) as? Map<*, *>
        val version = meta?.get("version") as? String ?: "latest"

        inputs.property("version", version)
    }

    @TaskAction
    fun download() {
        val targetDir = outputDir.dir("mathjax").get()
        val targetFile = targetDir.asFile

        val version: String by inputs.properties

        try {
            if (!targetFile.exists()) targetFile.mkdirs()

            targetDir.file("version").asFile.writeText(version)

            println("Downloading MathJax...")

            val filesToDownload = listOf(
                listOf("mathjax", version, "startup.js"),
                listOf("mathjax", version, "core.js"),
                listOf("mathjax", version, "adaptors/liteDOM.js"),
                listOf("mathjax", version, "input/tex.js"),
                listOf("mathjax", version, "output/svg.js"),
                listOf("@mathjax/mathjax-newcm-font", version, "svg.js"),
            )

            filesToDownload.forEach { pathParts ->
                val packageName = pathParts[0]
                val version = pathParts[1]
                val fileName = pathParts[2]

                val fileUrl = "https://cdn.jsdelivr.net/npm/$packageName@$version/$fileName"
                println("Downloading $fileName from $fileUrl...")
                URI(fileUrl).toURL().openStream().use { input ->
                    val targetOutput = targetDir.file("$packageName/$fileName").asFile
                    if (!targetOutput.parentFile.exists()) {
                        targetOutput.parentFile.mkdirs()
                    }
                    targetOutput.outputStream().use { input.copyTo(it) }
                }
            }

            val fileTreeApi = "https://data.jsdelivr.com/v1/packages/npm/mathjax@$version"
            println("Reading MathJax file tree from $fileTreeApi...")
            val fileTreeString = URI(fileTreeApi).toURL().readText()

            val fileTree = JsonSlurper().parseText(fileTreeString) as? Map<*, *>

            val extensions = (fileTree?.get("files") as? List<*>)
                ?.first { (it as? Map<*, *>)?.get("name") as? String == "input" }
                ?.let { (it as? Map<*, *>)?.get("files") as? List<*> }
                ?.first { (it as? Map<*, *>)?.get("name") as? String == "tex" }
                ?.let { (it as? Map<*, *>)?.get("files") as? List<*> }
                ?.first { (it as? Map<*, *>)?.get("name") as? String == "extensions" }
                ?.let { (it as? Map<*, *>)?.get("files") as? List<*> }
                ?.mapNotNull { ((it as? Map<*, *>)?.get("name") as? String) }
                ?: run {
                    System.err.println("Failed to find MathJax extensions in metadata")
                    null
                }

            extensions?.forEach { ext ->
                val fileUrl =
                    "https://cdn.jsdelivr.net/npm/mathjax@$version/input/tex/extensions/$ext"
                println("Downloading extension $ext from $fileUrl...")
                try {
                    URI(fileUrl).toURL().openStream().use { input ->
                        val targetOutput =
                            targetDir.file("mathjax/input/tex/extensions/$ext").asFile
                        if (!targetOutput.parentFile.exists()) {
                            targetOutput.parentFile.mkdirs()
                        }
                        targetOutput.outputStream().use { input.copyTo(it) }
                    }
                } catch (e: Throwable) {
                    System.err.println("Failed to download extension $ext: ${e.localizedMessage}")
                    e.printStackTrace()
                }
            }

            println("MathJax downloaded successfully.")
        } catch (e: Throwable) {
            println("Failed to download MathJax: ${e.localizedMessage}")
            throw e
        }
    }
}

abstract class DownloadRemendTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputDir.convention(
            project.layout.buildDirectory.dir("generated/remend/${name}"),
        )

        val api = "https://data.jsdelivr.com/v1/packages/npm/remend/resolved"
        val apiUrl = URI(api).toURL()

        val metaString = try {
            println("Reading Remend metadata from $api...")
            apiUrl.readText()
        } catch (e: Throwable) {
            println("Failed to read Remend metadata: ${e.localizedMessage}")
            e.printStackTrace()
            ""
        }

        val meta = JsonSlurper().parseText(metaString) as? Map<*, *>
        val version = meta?.get("version") as? String ?: "latest"

        inputs.property("version", version)
    }

    @TaskAction
    fun download() {
        val targetDir = outputDir.dir("remend").get()
        val targetFile = targetDir.asFile

        val version: String by inputs.properties

        try {
            if (!targetFile.exists()) targetFile.mkdirs()

            targetDir.file("version").asFile.writeText(version)

            println("Downloading Remend...")

            val fileUrl = "https://cdn.jsdelivr.net/npm/remend@$version/dist/index.js"
            println("Downloading index.js from $fileUrl...")
            URI(fileUrl).toURL().openStream().use { input ->
                val targetOutput = targetDir.file("index.js").asFile
                if (!targetOutput.parentFile.exists()) {
                    targetOutput.parentFile.mkdirs()
                }
                targetOutput.outputStream().use { input.copyTo(it) }
            }

            println("Remend downloaded successfully.")
        } catch (e: Throwable) {
            println("Failed to download Remend: ${e.localizedMessage}")
            throw e
        }
    }
}

androidComponents {
    onVariants { variant ->
        val mathJaxProvider = tasks.register<DownloadMathJaxTask>(
            "downloadMathJax${
                variant.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault(),
                    ) else it.toString()
                }
            }",
        )

        variant.sources.assets?.addGeneratedSourceDirectory(
            mathJaxProvider,
            DownloadMathJaxTask::outputDir,
        )

        val remendProvider = tasks.register<DownloadRemendTask>(
            "downloadRemend${
                variant.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault(),
                    ) else it.toString()
                }
            }",
        )

        variant.sources.assets?.addGeneratedSourceDirectory(
            remendProvider,
            DownloadRemendTask::outputDir,
        )
    }
}
