# C2PA Android library

This project provides Android bindings for the Content Authenticity (CAI). It wraps the [C2PA Rust implementation](https://github.com/contentauth/c2pa-rs) using its C API bindings to provide native Android support via an AAR library.

It offers:

- Android support via AAR library with Kotlin APIs
- Comprehensive support for C2PA manifest reading, validation, and creation
- Multiple signing methods: direct, callback, web service, and hardware-backed
- Hardware security integration with Android Keystore and StrongBox
- Signing server for development and testing of remote signing workflows

##  Prerequisites

- JDK 17 installed and `JAVA_HOME` set.
- Android SDK installed with `ANDROID_HOME` environment variable set.
    - Android API level 28+ (Android 9.0+)
- Android NDK installed (configure version in `local.properties` if needed).
- `Make` must be available on your system
- Connected Android device or emulator (for running test app).

Recommended: 

- Android Studio Hedgehog (2023.1.1) or newer

### NDK version

The build uses your default NDK version. To pin a specific NDK version, add it to your `local.properties` file:

```properties
ndk.version=29.0.13599879
```

## Quick start

Once you've installed or configured all the prerequisites, build the library and run the test app as follows:

```bash
# Clone the repository
git clone https://github.com/contentauth/c2pa-android.git
cd c2pa-android

# Build the library
make library

# Run the test app
make run-test-app
```

## Repository structure

- `/library` - Android library module with C2PA Kotlin APIs and JNI bindings
  - `/src/main/kotlin/org/contentauth/c2pa/` - Kotlin API classes:
    - `C2PA.kt` - Main API wrapper with stream-based operations
    - `StrongBoxSigner.kt` - Hardware-backed signing with StrongBox
    - `CertificateManager.kt` - Certificate generation and CSR management
    - `WebServiceSigner.kt` - Remote signing via web service
    - `KeyStoreSigner.kt` - Android Keystore signing
    - `Signer.kt`, `Builder.kt`, `Reader.kt` - Core C2PA API classes
    - `Stream.kt`, `FileStream.kt`, `MemoryStream.kt` - Stream implementations
  - `/src/main/jni` - JNI C implementation (`c2pa_jni.c`) and C2PA headers
  - `/src/androidTest` - Instrumented tests for the library
- `/test-shared` - Shared test modules used by both library instrumented tests and test-app
- `/test-app` - Test application with test UI for running all C2PA tests
- `/example-app` - Example Android application demonstrating real-world usage
- `/signing-server` - Ktor-based signing server for remote signing workflows
- `/Makefile` - Build system commands for downloading binaries and building

### JNI implementation

The Android library uses JNI (Java Native Interface).

**Native layer**

- C API headers: `library/src/main/jni/c2pa.h`
- JNI implementation: `library/src/main/jni/c2pa_jni.c`

## Installation

### Android with Gradle

The library is available from two sources:

- [JitPack](#installing-from-jitpack) (recommended for simplicity)
- [GitHub Packages](#installing-from-github-packages)

#### Installing from JitPack

Add JitPack to your repositories and include the dependency in your Gradle files:

In your `settings.gradle` or root `build.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

In your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.contentauth:c2pa-android:1.0.0'
}
```

#### Installing from GitHub Packages

GitHub Packages requires authentication. Add the repository and dependency:

In your root `build.gradle`:

```gradle
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
```

In your app's `build.gradle`:

```gradle
dependencies {
    implementation "org.contentauth:c2pa:1.0.0"
}
```

#### Development workflow

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

## Usage examples

### Reading and verifying manifests

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

### Signing content

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

### Using callback signers

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

### Using StrongBox hardware security

**Prerequisites:** This example requires a signing server for certificate enrollment. Start the server with:

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

### Using web service signing

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

## Makefile targets

The project includes a Makefile with the following targets:

**Library build:**

- `setup` - Create necessary directories
- `download-binaries` - Download pre-built binaries from GitHub releases
- `library` - Complete library build: setup, download, and build AAR
- `clean` - Remove build artifacts

**Testing:**

- `tests` - Run library instrumented tests (basic tests only; requires a device or emulator)
- `tests-with-server` - Run all tests with automatic signing server management (recommended)
- `coverage` - Generate test coverage report

> [!NOTE]
> Hardware and remote signing tests require the signing server. Use `make tests-with-server` for complete test coverage.

**Code quality:**

- `lint` - Run Android lint checks
- `format` - Format all Kotlin files with ktlint

**Signing server (hardware signing tests):**

- `signing-server-build` - Build the signing server
- `signing-server-run` - Run the signing server in the foreground
- `signing-server-start` - Start the signing server in the background
- `signing-server-stop` - Stop the signing server
- `signing-server-status` - Check if the signing server is running
- `signing-server-logs` - View signing server logs (`tail -f`)

**Apps:**

- `test-app` - Build the test app
- `example-app` - Build the example app
- `run-test-app` - Install and run the test app
- `run-example-app` - Install and run the example app

**Publishing:**

- `publish` - Publish the library to GitHub Packages

## Applications

The test application runs C2PA functionality tests with a visual UI. See [Project contributions - Test app](docs/project-contributions.md#test-app) for details.

### Example app

The example app lives in `example-app/`. It demonstrates C2PA integration in a camera application:

- Camera capture with C2PA manifest embedding
- Settings for configuring different signing modes
- Gallery view of signed images
- WebView integration for verifying content authenticity
- Complete implementation of all signing modes:
  - **Default:** Uses bundled test certificates for development (no configuration needed).
  - **Android Keystore:** Software-backed keys in Android Keystore (no configuration needed).
  - **Hardware security:** Hardware-backed keys (StrongBox or TEE) for maximum security (requires the signing server).
  - **Custom:** Upload your own certificates and private keys (requires certificate files).
  - **Remote:** Web service signing via the signing server (requires server URL and optional token).

**Configuration required**

Before using certain signing modes in the example app:

- **Hardware security:** Run the signing server (`make signing-server-start`).
- **Remote:** Enter the signing server URL and optional bearer token in **Settings**.
- **Custom:** Upload your own certificate and private key files via **Settings**.

**Running the example app**

```bash
# Build and run on connected device/emulator
make run-example-app

# Or open in Android Studio
# Open the example-app module and run it
```

**Signing server commands** (for hardware security and remote modes):

```bash
# Start the signing server
make signing-server-start

# Check server status
make signing-server-status

# View server logs
make signing-server-logs

# Stop server when done
make signing-server-stop
```

Alternatively, start the signing server in the foreground:

```bash
make signing-server-run
```

## API features

> [!NOTE]
> For full details on the API, see the [API reference documentation](https://contentauth.github.io/c2pa-android/c2pa-android/org.contentauth.c2pa/index.html).

### Core classes

- `C2PA` - Main entry point for static operations (reading files, signing)
- `Reader` - Reads and validates C2PA manifests from streams
- `Builder` - Creates and signs new C2PA manifests
- `Signer` - Signs manifests with various key types and methods
- `Stream` - Base class for stream operations
- `FileStream` - File-based stream implementation
- `MemoryStream` - Memory-based stream implementation
- `ByteArrayStream` - In-memory byte array stream implementation
- `DataStream` - Stream wrapper for byte arrays

### Signing classes

- `StrongBoxSigner` - Hardware-backed signing with StrongBox Keymaster
- `KeyStoreSigner` - Android Keystore-backed signing
- `WebServiceSigner` - Remote signing via web service
- `CertificateManager` - Certificate generation, CSR creation, and key management

### Signing options

1. **Direct signing** - Use private key and certificate PEM strings (`Signer.fromKeys()`).
2. **Callback signing** - Custom signing implementations for HSM, cloud KMS, and similar (`Signer.withCallback()`).
3. **Web service signing** - Remote signing via HTTP endpoints (`WebServiceSigner`).
4. **Hardware security** - Android Keystore and StrongBox integration:
   - `StrongBoxSigner` - Hardware-isolated signing with StrongBox Keymaster
   - `KeyStoreSigner` - Software-backed Android Keystore signing
   - `CertificateManager` - Certificate and CSR management
   - **Requirements:**
     - Android API 28+ (Android 9.0+)
     - StrongBox hardware support (falls back to TEE if unavailable)
     - External signing server or CA for certificate enrollment
     - Only ES256 (P-256) is supported for StrongBox

## License

This project is licensed under the Apache License, Version 2.0, and the MIT License. See [LICENSE-APACHE](LICENSE-APACHE) and [LICENSE-MIT](LICENSE-MIT) for details.
