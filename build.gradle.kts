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
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "8.13.0" apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    kotlin("jvm") version "2.2.10" apply false
    kotlin("plugin.serialization") version "2.2.10" apply false
    id("jacoco")
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }
val dokkaRuntime by configurations.creating
// Configuration for Dokka plugins (separate from CLI)
val dokkaPlugins by configurations.creating

dependencies {
    dokkaRuntime("org.jetbrains.dokka:dokka-cli:2.0.0")
    dokkaPlugins("org.jetbrains.dokka:dokka-base:2.0.0")
    dokkaPlugins("org.jetbrains.dokka:analysis-kotlin-descriptors:2.0.0")
}

// Task to generate documentation using Dokka CLI
tasks.register<JavaExec>("generateDocs") {
    group = "documentation"
    description = "Generate API documentation using Dokka CLI"

    classpath = dokkaRuntime
    mainClass.set("org.jetbrains.dokka.MainKt")

    val outputDir = file("$rootDir/build/docs")
    val sourceDir = file("$rootDir/library/src/main/kotlin")
    val moduleDoc = file("$rootDir/library/MODULE.md")

    doFirst {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        println("Generating documentation...")
        println("  Source: $sourceDir")
        println("  Module doc: $moduleDoc")
        println("  Output: $outputDir")
    }

    // Build plugin classpath from dokkaPlugins configuration only
    val pluginsClasspath = dokkaPlugins.files.joinToString(";") { it.absolutePath }

    // Build sourceSet argument with all parameters together
    val sourceSetParams = buildList {
        add("-src")
        add(sourceDir.absolutePath)
        if (moduleDoc.exists()) {
            add("-includes")
            add(moduleDoc.absolutePath)
        }
        add("-analysisPlatform")
        add("jvm")
        add("-documentedVisibilities")
        add("PUBLIC;PROTECTED;INTERNAL")
        add("-sourceSetName")
        add("main")
    }.joinToString(" ")

    args(
        "-pluginsClasspath", pluginsClasspath,
        "-outputDir", outputDir.absolutePath,
        "-moduleName", "c2pa-android",
        "-loggingLevel", "INFO",
        "-sourceSet", sourceSetParams,
    )
}
