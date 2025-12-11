/* 
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/

import org.gradle.api.publish.maven.MavenPublication
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("jacoco")
    `maven-publish`
}

android {
    namespace = "org.contentauth.c2pa"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // CMake configuration
        externalNativeBuild { cmake { arguments("-DANDROID_STL=c++_shared") } }

        // Specify ABIs to use prebuilt .so files
        ndk {
            abiFilters.add("x86_64")
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
            abiFilters.add("x86")
        }
    }

    // NDK version - can be overridden in local.properties with ndk.version=XX.X.XXXXXXX
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val localNdkVersion = localProperties.getProperty("ndk.version")
    if (localNdkVersion != null) {
        println("Using NDK version from local.properties: $localNdkVersion")
        ndkVersion = localNdkVersion
    } else {
        val defaultNdkVersion = "27.3.13750724"
        println("No NDK version specified in local.properties, using default: $defaultNdkVersion")
        ndkVersion = defaultNdkVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
        }
    }

    // CMake configuration for JNI code
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

    // Make sure to include JNI libs
    sourceSets { getByName("main") { jniLibs.srcDirs("src/main/jniLibs") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests { isIncludeAndroidResources = true } }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

// Set the base name for the AAR file
base { archivesName.set("c2pa") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jna)

    // BouncyCastle for CSR generation
    implementation(libs.bcprov.jdk15to18)
    implementation(libs.bcpkix.jdk15to18)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)

    androidTestImplementation(project(":test-shared"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.kotlin.test.junit)
}

// JaCoCo configuration
jacoco { toolVersion = "0.8.10" }

// Coverage report for instrumented tests only
tasks.register<JacocoReport>("jacocoInstrumentedTestReport") {
    dependsOn("createDebugCoverageReport")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*\$Lambda$*.*",
            "**/*\$inlined$*.*",
            "**/c2pa_jni.*", // Exclude JNI native code
        )

    val debugTree =
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) { exclude(fileFilter) }
    val kotlinDebugTree =
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(fileFilter) }

    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    classDirectories.setFrom(files(debugTree, kotlinDebugTree))

    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("outputs/code_coverage/debugAndroidTest/connected/**/coverage.ec")
        },
    )
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            groupId = "org.contentauth"
            artifactId = "c2pa"
            version = System.getenv("CI_COMMIT_TAG") ?: "1.0.0-SNAPSHOT"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: "contentauth/c2pa-android"
            url = uri("https://maven.pkg.github.com/$githubRepo")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Native library download configuration
val c2paVersion = project.properties["c2paVersion"] as String
val architectures =
    mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "armv7-linux-androideabi",
        "x86" to "i686-linux-android",
        "x86_64" to "x86_64-linux-android",
    )

tasks.register("setupDirectories") {
    doLast {
        val jniLibsDir = file("src/main/jniLibs")
        architectures.keys.forEach { arch -> file("$jniLibsDir/$arch").mkdirs() }
        file("src/main/jni").mkdirs()
    }
}

tasks.register("downloadNativeLibraries") {
    dependsOn("setupDirectories")

    doLast {
        println("Using C2PA version: $c2paVersion")
        val downloadDir = file("$rootDir/downloads")
        downloadDir.mkdirs()

        var headerDownloaded = false

        architectures.forEach { (arch, target) ->
            val libDir = file("src/main/jniLibs/$arch")
            val soFile = file("$libDir/libc2pa_c.so")

            if (!soFile.exists()) {
                println("Downloading C2PA library for $arch...")

                val zipFile = file("$downloadDir/$arch.zip")
                val extractDir = file("$downloadDir/$arch")

                // Download the zip file
                val url =
                    "https://github.com/contentauth/c2pa-rs/releases/download/c2pa-$c2paVersion/c2pa-$c2paVersion-$target.zip"
                println("Downloading from: $url")
                ant.invokeMethod(
                    "get",
                    mapOf("src" to url, "dest" to zipFile, "skipexisting" to "true"),
                )

                // Extract the zip file
                ant.invokeMethod(
                    "unzip",
                    mapOf("src" to zipFile, "dest" to extractDir, "overwrite" to "true"),
                )

                // Copy the .so file
                file("$extractDir/lib/libc2pa_c.so").copyTo(soFile, overwrite = true)

                // Copy header file from first architecture
                if (!headerDownloaded) {
                    val headerFile = file("$extractDir/include/c2pa.h")
                    if (headerFile.exists()) {
                        val destHeader = file("src/main/jni/c2pa.h")
                        headerFile.copyTo(destHeader, overwrite = true)

                        // Patch the header file
                        val content = destHeader.readText()
                        val patchedContent =
                            content.replace(
                                "typedef struct C2paSigner C2paSigner;",
                                "typedef struct C2paSigner { } C2paSigner;",
                            )
                        destHeader.writeText(patchedContent)

                        headerDownloaded = true
                        println("Patched c2pa.h header file")
                    }
                }
            } else {
                println("C2PA library for $arch already exists, skipping download")
            }
        }
    }
}

// Hook into the build process - download libraries before compilation if they don't exist
tasks.named("preBuild") { dependsOn("downloadNativeLibraries") }

// Clean downloaded native libraries
tasks.register("cleanDownloadedLibraries") {
    doLast {
        // Remove downloaded libraries
        architectures.keys.forEach { arch -> file("src/main/jniLibs/$arch/libc2pa_c.so").delete() }

        // Remove header file
        file("src/main/jni/c2pa.h").delete()

        // Remove downloads directory
        file("$rootDir/downloads").deleteRecursively()

        println("Cleaned downloaded native libraries")
    }
}

// Hook into clean task
tasks.named("clean") { dependsOn("cleanDownloadedLibraries") }
