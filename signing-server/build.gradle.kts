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
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "org.contentauth.c2pa"
version = "1.0.0"

sourceSets {
    main { java.srcDirs("src/main/kotlin") }
    test { java.srcDirs("src/test/kotlin") }
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-netty:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation("io.ktor:ktor-server-status-pages:3.2.3")
    implementation("io.ktor:ktor-server-call-logging:3.2.3")
    implementation("io.ktor:ktor-server-default-headers:3.2.3")
    implementation("io.ktor:ktor-server-config-yaml:3.2.3")
    implementation("io.ktor:ktor-server-auth-jvm:3.2.3")
    implementation("io.ktor:ktor-server-auth:3.2.3")
    implementation("com.typesafe:config:1.4.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests:2.3.13")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.2.3")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.contentauth.c2pa.signingserver.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}
