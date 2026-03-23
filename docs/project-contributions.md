# Contributing to the project 

The information in this page is primarily for those who wish to contribute to the c2pa-android project itself, rather than those who simply wish to use it in an application.  For general contribution guidelines, see [CONTRIBUTING](../CONTRIBUTING.md).


## Building from source

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

### Test app 

The comprehensive test application (`/test-app`) runs all C2PA functionality tests with a visual UI.

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
