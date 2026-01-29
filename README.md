# C2PA Android

This project provides Android bindings for the [C2PA](https://c2pa.org/) (Coalition for Content Provenance and Authenticity) libraries. It wraps the C2PA Rust implementation ([c2pa-rs](https://github.com/contentauth/c2pa-rs)) using its C API bindings to provide native Android support via an AAR library.

## Overview

C2PA Android offers:

- Android support via AAR library with Kotlin APIs
- Comprehensive support for C2PA manifest reading, validation, and creation
- Multiple signing methods: direct, callback, web service, and hardware-backed
- Hardware security integration with Android Keystore and StrongBox
- Signing server for development and testing of remote signing workflows

## Quick Start

**Prerequisites:**
- JDK 17 installed and `JAVA_HOME` set
- Android SDK installed with `ANDROID_HOME` environment variable set
- Android NDK installed (configure version in `local.properties` if needed)
- Make available on your system
- Connected Android device or emulator (for running test app)

```bash
# Clone the repository
git clone https://github.com/contentauth/c2pa-android.git
cd c2pa-android

# Build the library
make library

# Run the test app
make run-test-app
```

## Repository Structure

- `/library` - Android library module with C2PA Kotlin APIs and JNI bindings
  - `/src/main/kotlin/org/contentauth/c2pa/` - Kotlin API classes:
    - `C2PA.kt` - Main API wrapper with stream-based operations
    - `StrongBoxSigner.kt` - Hardware-backed signing with StrongBox
    - `CertificateManager.kt` - Certificate generation and CSR management
    - `WebServiceSigner.kt` - Remote signing via web service
    - `KeyStoreSigner.kt` - Android Keystore signing
    - `Signer.kt`, `Builder.kt`, `Reader.kt` - Core C2PA API classes
    - `Stream.kt`, `FileStream.kt`, `MemoryStream.kt` - Stream implementations
  - `/src/main/jni` - JNI C implementation (c2pa_jni.c) and C2PA headers
  - `/src/androidTest` - Instrumented tests for the library
- `/test-shared` - Shared test modules used by both library instrumented tests and test-app
- `/test-app` - Test application with test UI for running all C2PA tests
- `/example-app` - Example Android application demonstrating real-world usage
- `/signing-server` - Ktor-based signing server for remote signing workflows
- `/Makefile` - Build system commands for downloading binaries and building

## Requirements

### Android

- Android API level 28+ (Android 9.0+)
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17

### Development

- JDK 17 (for Android builds)
- Android SDK (for Android builds)
- Android NDK (any recent version - see note below)
- Make

#### NDK Version

The project will use your default NDK version. If you need to use a specific NDK version, add it to your `local.properties` file:

```properties
ndk.version=29.0.13599879
```

## Installation

### Android (Gradle)

The library is available from two sources: **JitPack** (recommended for simplicity) and **GitHub Packages**.

#### Option 1: JitPack (Recommended)

Add JitPack to your repositories and include the dependency:

```gradle
// In your settings.gradle or root build.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// In your app's build.gradle
dependencies {
    implementation 'com.github.contentauth:c2pa-android:1.0.0'
}
```

#### Option 2: GitHub Packages

GitHub Packages requires authentication. Add the repository and dependency:

```gradle
// In your root build.gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/contentauth/c2pa-android")
            credentials {
                username = System.getenv("GITHUB_USER") ?: project.findProperty("GITHUB_USER")
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("GITHUB_TOKEN")
            }
        }
    }
}

// In your app's build.gradle
dependencies {
    implementation "org.contentauth:c2pa:1.0.0"
}
```

#### Development Workflow for Android

For local development without using a released version:

1. Build the library with `make library`
2. The AAR will be available at `library/build/outputs/aar/c2pa-release.aar`
3. Add the AAR to your project:

```gradle
// In app/build.gradle
dependencies {
    implementation files('path/to/c2pa-release.aar')    
    implementation 'net.java.dev.jna:jna:5.17.0@aar'
}
```

## Usage

### Android Examples

#### Reading and Verifying Manifests

```kotlin
import org.contentauth.c2pa.*

// Read a manifest from a file
try {
    val manifest = C2PA.readFile("/path/to/image.jpg")
    println("Manifest: $manifest")
} catch (e: C2PAError) {
    println("Error reading manifest: $e")
}

// Read from a stream
val imageStream = FileStream(File("/path/to/image.jpg"), FileStream.Mode.READ)
try {
    val reader = Reader.fromStream("image/jpeg", imageStream)
    val manifestJson = reader.json()
    println("Manifest JSON: $manifestJson")
    reader.close()
} finally {
    imageStream.close()
}
```

#### Signing Content

```kotlin
// Sign with built-in signer
val signerInfo = SignerInfo(
    algorithm = SigningAlgorithm.ES256,
    certificatePEM = certsPem,
    privateKeyPEM = privateKeyPem,
    tsaURL = "https://timestamp.server.com"
)

val manifest = """{
    "claim_generator": "my_app/1.0",
    "assertions": [
        {"label": "c2pa.actions", "data": {"actions": [{"action": "c2pa.created"}]}}
    ]
}"""

try {
    C2PA.signFile(
        sourcePath = "/path/to/input.jpg",
        destPath = "/path/to/output.jpg",
        manifest = manifest,
        signerInfo = signerInfo
    )
} catch (e: C2PAError) {
    println("Signing failed: $e")
}
```

#### Using Callback Signers

```kotlin
// Create a callback signer for custom signing implementations
val callbackSigner = Signer.withCallback(
    algorithm = SigningAlgorithm.ES256,
    certificateChainPEM = certsPem,
    tsaURL = null
) { data ->
    // Custom signing logic here
    myCustomSigningFunction(data)
}

// Use with Builder API
val builder = Builder.fromJson(manifestJson)
val sourceStream = FileStream(File("/path/to/input.jpg"), FileStream.Mode.READ)
val destStream = FileStream(File("/path/to/output.jpg"), FileStream.Mode.WRITE)

try {
    val result = builder.sign("image/jpeg", sourceStream, destStream, callbackSigner)
    println("Signed successfully, size: ${result.size}")
} finally {
    builder.close()
    sourceStream.close()
    destStream.close()
    callbackSigner.close()
}
```

#### Using Hardware Security (StrongBox)

**Prerequisites**: This example requires a signing server for certificate enrollment. Start the server with:
```bash
make signing-server-start
```

Hardware-backed signing requires obtaining a certificate from a Certificate Authority or signing server. Here's the complete workflow:

```kotlin
import org.contentauth.c2pa.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.security.KeyStore

val keyAlias = "my-strongbox-key"

// Step 1: Create or use existing hardware-backed key
val keyStore = KeyStore.getInstance("AndroidKeyStore")
keyStore.load(null)

if (!keyStore.containsAlias(keyAlias)) {
    val config = StrongBoxSigner.Config(
        keyTag = keyAlias,
        requireUserAuthentication = false
    )
    
    // Create key using StrongBoxSigner (uses StrongBox if available, TEE otherwise)
    StrongBoxSigner.createKey(config)
}

// Step 2: Generate a Certificate Signing Request (CSR)
val certificateConfig = CertificateManager.CertificateConfig(
    commonName = "My App",
    organization = "My Organization",
    organizationalUnit = "Mobile",
    country = "US",
    state = "CA",
    locality = "San Francisco"
)

val csr = CertificateManager.createCSR(keyAlias, certificateConfig)

// Step 3: Submit CSR to signing server to get signed certificate
val client = OkHttpClient()
// Note: 10.0.2.2 is the Android emulator's address for localhost
val enrollUrl = "http://10.0.2.2:8080/api/v1/certificates/sign"
val requestBody = JSONObject().apply { put("csr", csr) }.toString()

val request = Request.Builder()
    .url(enrollUrl)
    .post(requestBody.toRequestBody("application/json".toMediaType()))
    .build()

val response = client.newCall(request).execute()
if (!response.isSuccessful) {
    throw Exception("Certificate enrollment failed: ${response.code}")
}

val certChain = JSONObject(response.body?.string() ?: "")
    .getString("certificate_chain")

// Step 4: Create signer with hardware-backed key and certificate
val config = StrongBoxSigner.Config(
    keyTag = keyAlias,
    requireUserAuthentication = false
)

val signer = StrongBoxSigner.createSigner(
    algorithm = SigningAlgorithm.ES256,
    certificateChainPEM = certChain,
    config = config,
    tsaURL = null
)

// Step 5: Use the signer
val builder = Builder.fromJson(manifestJson)
val sourceStream = FileStream(File("/path/to/input.jpg"), FileStream.Mode.READ)
val destStream = FileStream(File("/path/to/output.jpg"), FileStream.Mode.WRITE)

try {
    builder.sign("image/jpeg", sourceStream, destStream, signer)
} finally {
    builder.close()
    sourceStream.close()
    destStream.close()
    signer.close()
}
```

#### Using Web Service Signing

```kotlin
import org.contentauth.c2pa.*
import kotlinx.coroutines.runBlocking

// Create a WebServiceSigner that connects to a remote signing server
// Note: 10.0.2.2 is the Android emulator's address for localhost on the host machine
// For physical devices, use your computer's IP address on the local network
val webServiceSigner = WebServiceSigner(
    configurationURL = "http://10.0.2.2:8080/api/v1/c2pa/configuration",
    bearerToken = "your-token-here"  // Optional authentication token
)

// Create the signer (fetches configuration from the server)
val signer = runBlocking {
    webServiceSigner.createSigner()
}

// Use the signer with Builder API
val builder = Builder.fromJson(manifestJson)
val sourceStream = FileStream(File("/path/to/input.jpg"), FileStream.Mode.READ)
val destStream = FileStream(File("/path/to/output.jpg"), FileStream.Mode.WRITE)

try {
    builder.sign("image/jpeg", sourceStream, destStream, signer)
} finally {
    builder.close()
    sourceStream.close()
    destStream.close()
    signer.close()
}
```

## Building from Source

1. Clone this repository:

   ```bash
   git clone https://github.com/contentauth/c2pa-android.git
   cd c2pa-android
   ```

2. Set up the required dependencies:
   - Set up JDK 17
   - Set up Android SDK
   - Set up environment variables (add to your shell profile):

     ```bash
     export JAVA_HOME=$(/usr/libexec/java_home -v 17)
     export ANDROID_HOME=$HOME/Library/Android/sdk
     ```

3. (Optional) Update C2PA version:
   - Edit `library/gradle.properties` and change `c2paVersion`
   - See available versions at https://github.com/contentauth/c2pa-rs/releases

4. Build the library:

   ```bash
   # Complete build: setup, download binaries, and build AAR
   make library
   ```

5. Check built outputs:

   ```bash
   # Android Library AAR
   ls -la library/build/outputs/aar/
   ```

## Makefile Targets

The project includes a comprehensive Makefile with the following targets:

**Library Build:**
- `setup` - Create necessary directories
- `download-binaries` - Download pre-built binaries from GitHub releases
- `library` - Complete library build: setup, download, and build AAR
- `clean` - Remove build artifacts

**Testing:**
- `tests` - Run library instrumented tests (basic tests only, requires device/emulator)
- `tests-with-server` - Run all tests with automatic signing server management (RECOMMENDED)
- `coverage` - Generate test coverage report

**Note**: Hardware and remote signing tests require the signing server. Use `make tests-with-server` for complete test coverage.

**Code Quality:**
- `lint` - Run Android lint checks
- `format` - Format all Kotlin files with ktlint

**Signing Server (for hardware signing tests):**
- `signing-server-build` - Build the signing server
- `signing-server-run` - Run the signing server in foreground
- `signing-server-start` - Start the signing server in background
- `signing-server-stop` - Stop the signing server
- `signing-server-status` - Check if signing server is running
- `signing-server-logs` - View signing server logs (tail -f)

**Apps:**
- `test-app` - Build the test app
- `example-app` - Build the example app
- `run-test-app` - Install and run the test app
- `run-example-app` - Install and run the example app

**Publishing:**
- `publish` - Publish library to GitHub packages

## Continuous Integration & Releases

This project uses GitHub Actions for continuous integration and release management:

### Release Process

The release process is automated through a single workflow:

1. **Start a Release**:
   - Trigger the "Release" workflow from the Actions tab
   - Enter the version number (e.g., `v1.0.0`)

2. **Automated Build and Release**:
   - Downloads pre-built C2PA binaries
   - Builds the Android AAR package
   - Creates a GitHub release with the specified version
   - Attaches the Android AAR artifact
   - Publishes documentation for integration

## Applications

### Test App (`/test-app`)

A comprehensive test application that runs all C2PA functionality tests with a visual UI:
- Uses shared test modules from `/test-shared`:
  - `CoreTests` - Library version, error handling, manifest reading
  - `StreamTests` - File, memory, and byte array stream operations
  - `BuilderTests` - Builder API with ingredients and resources
  - `SignerTests` - All signing methods including hardware security
  - `WebServiceTests` - Remote signing integration
- Visual test results with success/failure indicators and detailed logs
- Tests all signing modes: default (bundled certificates), Android Keystore, hardware (StrongBox), custom certificates, and remote signing
- Includes 30+ tests covering the complete C2PA API surface

**Running the test app:**
```bash
# Build and run on connected device/emulator
make run-test-app

# Or open in Android Studio
# Open the test-app module and run it
```

### Example App (`/example-app`)

A real-world demonstration app showcasing C2PA integration in a camera application:
- Camera capture with C2PA manifest embedding
- Settings for configuring different signing modes
- Gallery view of signed images
- WebView integration for verifying content authenticity
- Complete implementation of all signing modes:
  - **Default**: Uses bundled test certificates for development (no configuration needed)
  - **Android Keystore**: Software-backed keys in Android Keystore (no configuration needed)
  - **Hardware Security**: StrongBox/TEE-backed keys for maximum security (requires signing server)
  - **Custom**: Upload your own certificates and private keys (requires certificate files)
  - **Remote**: Web service signing via signing server (requires server URL and optional token)

**Configuration Required:**
Before using certain signing modes in the example app:
- **Hardware Security**: Requires signing server running (`make signing-server-start`)
- **Remote**: Requires entering signing server URL and optional bearer token in Settings
- **Custom**: Requires uploading your own certificate and private key files via Settings

**Running the example app:**
```bash
# Build and run on connected device/emulator
make run-example-app

# Or open in Android Studio
# Open the example-app module and run it
```

**Signing server commands** (for Hardware Security and Remote modes):
```bash
# Start the signing server
make signing-server-start

# Check server status
make signing-server-status

# View server logs
make signing-server-logs

# Stop server when done
make signing-server-stop

or 

# Start the signing server in the foreground
make signing-server-run
```

## API Features

### Core Classes

- **C2PA** - Main entry point for static operations (reading files, signing)
- **Reader** - For reading and validating C2PA manifests from streams
- **Builder** - For creating and signing new C2PA manifests
- **Signer** - For signing manifests with various key types and methods
- **Stream** - Base class for stream operations
- **FileStream** - File-based stream implementation
- **MemoryStream** - Memory-based stream implementation
- **ByteArrayStream** - In-memory byte array stream implementation
- **DataStream** - Stream wrapper for byte arrays

### Signing Classes

- **StrongBoxSigner** - Hardware-backed signing with StrongBox Keymaster
- **KeyStoreSigner** - Android Keystore-backed signing
- **WebServiceSigner** - Remote signing via web service
- **CertificateManager** - Certificate generation, CSR creation, and key management

### Signing Options

1. **Direct Signing** - Using private key and certificate PEM strings (`Signer.fromKeys()`)
2. **Callback Signing** - Custom signing implementations for HSM, cloud KMS, etc. (`Signer.withCallback()`)
3. **Web Service Signing** - Remote signing via HTTP endpoints (`WebServiceSigner`)
4. **Hardware Security** - Android Keystore and StrongBox integration
   - `StrongBoxSigner` - Hardware-isolated signing with StrongBox Keymaster
   - `KeyStoreSigner` - Software-backed Android Keystore signing
   - `CertificateManager` - Certificate and CSR management
   - **Requirements:**
     - Android API 28+ (Android 9.0+)
     - StrongBox hardware support (automatically falls back to TEE if unavailable)
     - External signing server or CA for certificate enrollment
     - Only ES256 (P-256) algorithm supported for StrongBox

## Testing

The project includes comprehensive instrumented tests that validate the C2PA functionality through the JNI bridge:

### Instrumented Tests
Run instrumented tests on a connected device or emulator:
```bash
make tests-with-server
```

### Test Coverage
Generate test coverage reports:
```bash
make coverage
```

Coverage reports will be available at:
- HTML: `library/build/reports/jacoco/jacocoInstrumentedTestReport/html/index.html`
- XML: `library/build/reports/jacoco/jacocoInstrumentedTestReport/jacocoInstrumentedTestReport.xml`

The project uses JaCoCo for coverage reporting. Coverage reports are generated during CI builds and stored as artifacts.

## JNI Implementation

The Android library uses JNI (Java Native Interface):

- **Native Layer:**
  - C API headers: `library/src/main/jni/c2pa.h`
  - JNI implementation: `library/src/main/jni/c2pa_jni.c`

## License

This project is licensed under the Apache License, Version 2.0 and MIT - see the LICENSE-APACHE and LICENSE-MIT files for details.
