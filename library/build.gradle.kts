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
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties
import java.util.zip.ZipInputStream

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.dokka")
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
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
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
    sourceSets { getByName("main") { jniLibs.directories.add("src/main/jniLibs") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dokka {
    moduleName.set("c2pa-android")
    dokkaPublications.html {
        outputDirectory.set(rootDir.resolve("build/docs"))
    }
    dokkaSourceSets.register("main") {
        sourceRoots.from("src/main/kotlin")
        includes.from("MODULE.md")
        documentedVisibilities(
            org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public,
            org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Protected,
            org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Internal,
        )
    }
}

// Set the base name for the AAR file
base { archivesName.set("c2pa") }

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("net.java.dev.jna:jna:5.17.0@aar")

    // BouncyCastle for CSR generation
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")

    androidTestImplementation(project(":test-shared"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")
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
    val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
    val jniDir = layout.projectDirectory.dir("src/main/jni")
    val archKeys = architectures.keys.toList()
    doLast {
        archKeys.forEach { arch -> jniLibsDir.dir(arch).asFile.mkdirs() }
        jniDir.asFile.mkdirs()
    }
}

tasks.register("downloadNativeLibraries") {
    dependsOn("setupDirectories")

    val projectDir = layout.projectDirectory
    val downloadDir = rootDir.resolve("downloads")
    val jniLibsDir = projectDir.dir("src/main/jniLibs")
    val destHeader = projectDir.file("src/main/jni/c2pa.h")
    val archMap = architectures.toMap()
    val version = c2paVersion

    doLast {
        println("Using C2PA version: $version")
        downloadDir.mkdirs()

        var headerDownloaded = false

        archMap.forEach { (arch, target) ->
            val soFile = jniLibsDir.file("$arch/libc2pa_c.so").asFile

            if (!soFile.exists()) {
                println("Downloading C2PA library for $arch...")

                val zipFile = downloadDir.resolve("$arch.zip")
                val extractDir = downloadDir.resolve(arch)

                // Download the zip file
                val url =
                    "https://github.com/contentauth/c2pa-rs/releases/download/c2pa-$version/c2pa-$version-$target.zip"
                println("Downloading from: $url")
                if (!zipFile.exists()) {
                    val connection = URI(url).toURL().openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.inputStream.use { input ->
                        zipFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                // Extract the zip file
                extractDir.mkdirs()
                val zipInputStream = ZipInputStream(zipFile.inputStream())
                zipInputStream.use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = extractDir.resolve(entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile.mkdirs()
                            outFile.outputStream().use { output -> zis.copyTo(output) }
                        }
                        entry = zis.nextEntry
                    }
                }

                // Copy the .so file
                extractDir.resolve("lib/libc2pa_c.so").copyTo(soFile, overwrite = true)

                // Copy header file from first architecture
                if (!headerDownloaded) {
                    val headerFile = extractDir.resolve("include/c2pa.h")
                    if (headerFile.exists()) {
                        val dest = destHeader.asFile
                        headerFile.copyTo(dest, overwrite = true)

                        // Patch the header file
                        val content = dest.readText()
                        val patchedContent =
                            content.replace(
                                "typedef struct C2paSigner C2paSigner;",
                                "typedef struct C2paSigner { } C2paSigner;",
                            )
                        dest.writeText(patchedContent)

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

// Hook into the build process - download libraries before compilation if they don't exist.
// AGP 9 no longer wires the per-ABI CMake tasks through preBuild for JNI sources, so depend on
// downloadNativeLibraries from those tasks directly as well.
tasks.matching {
    it.name == "preBuild" ||
        it.name.startsWith("configureCMake") ||
        it.name.startsWith("buildCMake")
}.configureEach { dependsOn("downloadNativeLibraries") }

// Clean downloaded native libraries
tasks.register("cleanDownloadedLibraries") {
    val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
    val headerFile = layout.projectDirectory.file("src/main/jni/c2pa.h")
    val downloadsDir = rootDir.resolve("downloads")
    val archKeys = architectures.keys.toList()
    doLast {
        // Remove downloaded libraries
        archKeys.forEach { arch -> jniLibsDir.file("$arch/libc2pa_c.so").asFile.delete() }

        // Remove header file
        headerFile.asFile.delete()

        // Remove downloads directory
        downloadsDir.deleteRecursively()

        println("Cleaned downloaded native libraries")
    }
}

// Hook into clean task
tasks.named("clean") { dependsOn("cleanDownloadedLibraries") }
