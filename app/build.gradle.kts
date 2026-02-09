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
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = packageName
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        optIn.add("kotlin.ExperimentalUnsignedTypes")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
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

        val mainMetaString = try {
            println("Reading MathJax metadata from $api...")
            apiUrl.readText()
        } catch (e: Throwable) {
            println("Failed to read MathJax metadata: ${e.localizedMessage}")
            e.printStackTrace()
            ""
        }

        val meta = JsonSlurper().parseText(mainMetaString) as? Map<*, *>
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

            println("MathJax downloaded successfully.")
        } catch (e: Throwable) {
            println("Failed to download MathJax: ${e.localizedMessage}")
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
    }
}
