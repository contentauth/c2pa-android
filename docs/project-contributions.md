# Contributing to the project 

The information in this page is primarily for those who wish to contribute to the c2pa-android project itself, rather than those who simply wish to use it in an application.  For general contribution guidelines, see [CONTRIBUTING](../CONTRIBUTING.md).

## Building from source

1. Clone this repository:

   ```bash
   git clone https://github.com/contentauth/c2pa-android.git
   cd c2pa-android
   ```

2. Set up the required dependencies:
   - Set up a JDK matching the `java` entry in `gradle/libs.versions.toml`
   - Set up Android SDK
   - Set up environment variables (add to your shell profile):

     ```bash
     # <version> is the `java` entry in gradle/libs.versions.toml
     export JAVA_HOME=$(/usr/libexec/java_home -v <version>)
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

## Testing

Instrumented tests validate C2PA functionality through the JNI bridge.

### Instrumented tests

Run instrumented tests on a connected device or emulator:
```bash
make tests-with-server
```

### Test coverage

Generate test coverage reports:
```bash
make coverage
```

Coverage reports will be available at:
- HTML: `library/build/reports/jacoco/jacocoInstrumentedTestReport/html/index.html`
- XML: `library/build/reports/jacoco/jacocoInstrumentedTestReport/jacocoInstrumentedTestReport.xml`


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

The project uses JaCoCo for coverage reporting. Coverage reports are generated during CI builds and stored as artifacts.

## Continuous integration and releases

This project uses GitHub Actions for continuous integration and release management.

The release process is automated through a single workflow:

1. **Start a release:**
   - Trigger the **Release** workflow from the Actions tab.
   - Enter the version number (for example, `v1.0.0`).

2. **Automated build and release:**
   - Download pre-built C2PA binaries.
   - Build the Android AAR package.
   - Create a GitHub release with the specified version.
   - Attach the Android AAR artifact.
   - Publish documentation for integration.

## Building documentation locally

To build the selected releases, install Bash 3.2 or newer, `jq`, Git, the GitHub CLI
(`gh`), and the JDK and Android SDK required by those releases. Authenticate `gh`
or set `GH_TOKEN` to a token with read access to the repository, then run:

```bash
git fetch origin --tags
make docs-versioned
```

The output is `build/docs-versioned/`, containing the default site and
`versions/<tag>/` for every configured release, including the default.
All documentation and selector links are relative, so this directory can be
served over HTTP locally or hosted under `/c2pa-android/` on GitHub Pages.

The builder verifies that each tag is a published GitHub release, exports its
sources to a temporary workspace, and runs that release's own `make docs`, so
each release builds with the recipe it shipped with. All releases build with the
one JDK the workflow selects, which must therefore still build the oldest listed
release. It preserves executable permissions on the Gradle wrapper and
does not pass `GH_TOKEN` or `GITHUB_TOKEN` to Gradle. Release builds execute the
tagged Gradle scripts, so only add trusted release tags. Temporary workspaces are
removed after success or failure, and existing versioned output is replaced only
after all selected releases build successfully.

The existing `make docs` command still generates working-tree documentation in
`build/docs/`; it does not replace the versioned site. `make docs-versioned` fails
if a release has no library or package landing page, a page has no head element
or version slot to carry the selector, or the assembled site's root is not the
default version, so the real build is the test.

### Maintaining versions

The release workflow puts each stable release first in
[docs/versions.json](versions.json), commits that to the release branch
before tagging, and then rebuilds and deploys the site with that release as the
default. It calls the documentation workflow explicitly, because events created
with `GITHUB_TOKEN` do not trigger other workflows. Pre-releases are not added
and do not rebuild the site. To document another release or change the default,
edit the list and keep the default first. The documentation workflow also
deploys on relevant changes to `main` and manual dispatch, and validates pull
requests without deploying them.