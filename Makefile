.PHONY: all clean setup library publish download-binaries tests coverage help test-app example-app \
        run-test-app run-example-app signing-server-start signing-server-stop signing-server-status \
        signing-server-build tests-with-server lint format docs docs-clean

# Default target
all: library

# Setup directories using Gradle task
setup:
	@echo "Setting up directories..."
	@./gradlew :library:setupDirectories

# Download pre-built binaries from GitHub releases using Gradle task
download-binaries:
	@echo "Downloading pre-built binaries..."
	@./gradlew :library:downloadNativeLibraries

# Complete library build: setup, download binaries, and build
library: setup download-binaries
	@echo "Building library..."
	@./gradlew :library:clean :library:assembleRelease
	@echo "Library build completed. AAR available at library/build/outputs/aar/c2pa-release.aar"

# Run all tests including hardware signing tests (requires device/emulator)
# If signing server tests are included, start the server first with: make signing-server-start
tests:
	@echo "Running all instrumented tests..."
	@./gradlew :library:connectedDebugAndroidTest :test-app:app:connectedDebugAndroidTest

# Generate code coverage report (requires device/emulator)
coverage:
	@echo "Running instrumented tests with coverage..."
	@./gradlew :library:createDebugCoverageReport :library:jacocoInstrumentedTestReport
	@echo "Coverage report generated at library/build/reports/jacoco/jacocoInstrumentedTestReport/html/index.html"

# Build test app
test-app:
	@echo "Building test app..."
	@./gradlew :test-app:app:build
	@echo "Test app build completed"

# Build example app
example-app:
	@echo "Building example app..."
	@./gradlew :example-app:app:build
	@echo "Example app build completed"

# Run test app
run-test-app:
	@echo "Installing and running test app..."
	@./gradlew :test-app:app:installDebug
	@adb shell am start -n org.contentauth.c2pa.testapp/org.contentauth.c2pa.testapp.MainActivity

# Run example app
run-example-app:
	@echo "Installing and running example app..."
	@./gradlew :example-app:app:installDebug
	@adb shell am start -n org.contentauth.c2pa.exampleapp/org.contentauth.c2pa.exampleapp.MainActivity

# Publish library to GitHub packages
publish:
	@echo "Publishing library to GitHub packages..."
	@./gradlew :library:publish

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@./gradlew clean

# Run Android lint to check for code issues
lint:
	@echo "Running Android lint check..."
	@./gradlew :library:lint :example-app:app:lint

# Format all Kotlin files with ktlint
# Note: Requires ktlint to be installed (brew install ktlint)
format:
	@echo "Formatting all Kotlin files with ktlint..."
	@if command -v ktlint >/dev/null 2>&1; then \
		find . -type f \( -name "*.kt" -o -name "*.kts" \) ! -path "*/build/*" -exec ktlint --format {} + ; \
	else \
		echo "Error: ktlint not found"; \
		echo "Install with: brew install ktlint"; \
		exit 1; \
	fi

# Generate API documentation using Dokka
docs:
	@echo "Generating API documentation..."
	@./gradlew generateDocs
	@echo ""
	@echo "Documentation generation complete!"
	@echo "Output: build/docs/index.html"
	@echo "To view: open build/docs/index.html"

# Clean generated documentation
docs-clean:
	@echo "Cleaning generated documentation..."
	@rm -rf build/docs
	@rm -f library/build/libs/c2pa-release-javadoc.jar
	@echo "Documentation cleaned"

# File to store the server PID
SIGNING_SERVER_PID_FILE := .signing-server.pid

# Build the signing server (downloads native libs and compiles JNI)
signing-server-build:
	@echo "Building signing server..."
	@./gradlew :signing-server:build
	@echo "Signing server build completed"

# Start the signing server in the background
signing-server-start: signing-server-build
	@if [ -f $(SIGNING_SERVER_PID_FILE) ]; then \
		PID=$$(cat $(SIGNING_SERVER_PID_FILE)); \
		if ps -p $$PID > /dev/null 2>&1; then \
			echo "Signing server already running with PID $$PID"; \
			exit 0; \
		fi; \
	fi
	@echo "Starting signing server on port 8080..."
	@BEARER_TOKEN=test-12345 SIGNING_SERVER_URL=http://10.0.2.2:8080 nohup ./gradlew :signing-server:run > signing-server.log 2>&1 & \
		echo $$! > $(SIGNING_SERVER_PID_FILE)

# Run signing server in foreground (for development)
signing-server-run:
	@if [ ! -f "./gradlew" ]; then \
		echo "Error: gradlew not found"; \
		exit 1; \
	fi
	./gradlew :signing-server:build && BEARER_TOKEN=test-12345 SIGNING_SERVER_URL=http://localhost:8080 ./gradlew :signing-server:run
	@echo "Waiting for server to start..."
	@for i in {1..10}; do \
		if curl -s http://localhost:8080/health > /dev/null 2>&1; then \
			echo "Signing server started successfully (PID: $$(cat $(SIGNING_SERVER_PID_FILE)))"; \
			echo "Server logs: tail -f signing-server.log"; \
			exit 0; \
		fi; \
		echo "  Waiting... ($$i/10)"; \
		sleep 2; \
	done; \
	echo "Failed to start signing server"; \
	rm -f $(SIGNING_SERVER_PID_FILE); \
	exit 1

# Stop the signing server
signing-server-stop:
	@if [ -f $(SIGNING_SERVER_PID_FILE) ]; then \
		PID=$$(cat $(SIGNING_SERVER_PID_FILE)); \
		if ps -p $$PID > /dev/null 2>&1; then \
			echo "Stopping signing server (PID: $$PID)..."; \
			kill $$PID; \
			rm -f $(SIGNING_SERVER_PID_FILE); \
			echo "Signing server stopped"; \
		else \
			echo "Signing server not running (stale PID file)"; \
			rm -f $(SIGNING_SERVER_PID_FILE); \
		fi; \
	else \
		echo "Signing server not running"; \
	fi

# Check signing server status
signing-server-status:
	@if [ -f $(SIGNING_SERVER_PID_FILE) ]; then \
		PID=$$(cat $(SIGNING_SERVER_PID_FILE)); \
		if ps -p $$PID > /dev/null 2>&1; then \
			echo "Signing server is running (PID: $$PID)"; \
			curl -s http://localhost:8080/ | python3 -m json.tool 2>/dev/null || \
				curl -s http://localhost:8080/; \
			echo ""; \
		else \
			echo "Signing server not running (stale PID file)"; \
			rm -f $(SIGNING_SERVER_PID_FILE); \
		fi; \
	else \
		echo "Signing server not running"; \
	fi

# View signing server logs
signing-server-logs:
	@if [ -f signing-server.log ]; then \
		tail -f signing-server.log; \
	else \
		echo "No server logs found. Start the server first with: make signing-server-start"; \
	fi

# Run all tests with automatic signing server management and generate coverage
tests-with-server: signing-server-start
	@echo "Running all tests with signing server..."
	@$(MAKE) tests || ($(MAKE) signing-server-stop; exit 1)
	@echo "Generating coverage reports..."
	@./gradlew :library:createDebugAndroidTestCoverageReport :library:jacocoInstrumentedTestReport || ($(MAKE) signing-server-stop; exit 1)
	@$(MAKE) signing-server-stop
	@echo "All tests completed with coverage reports generated"
	@echo "Coverage report: library/build/reports/jacoco/jacocoInstrumentedTestReport/html/index.html"

# Helper to show available targets
help:
	@echo "Available targets:"
	@echo ""
	@echo "Library Build:"
	@echo "  setup                 - Create necessary directories"
	@echo "  download-binaries     - Download pre-built binaries from GitHub releases"
	@echo "  library               - Complete library build (default)"
	@echo "  clean                 - Remove build artifacts"
	@echo ""
	@echo "Testing:"
	@echo "  tests                 - Run all tests including hardware signing tests"
	@echo "  tests-with-server     - Run all tests with automatic signing server management"
	@echo "  coverage              - Generate test coverage report (requires device)"
	@echo ""
	@echo "Code Quality:"
	@echo "  lint                  - Run Android lint checks"
	@echo "  format                - Format all Kotlin files with ktlint"
	@echo ""
	@echo "Documentation:"
	@echo "  docs                  - Generate API documentation with Dokka"
	@echo "  docs-clean            - Clean generated documentation"
	@echo ""
	@echo "Signing Server (for hardware signing tests):"
	@echo "  signing-server-build  - Build the signing server"
	@echo "  signing-server-run    - Run the signing server in foreground"
	@echo "  signing-server-start  - Start the signing server in background"
	@echo "  signing-server-stop   - Stop the signing server"
	@echo "  signing-server-status - Check if signing server is running"
	@echo "  signing-server-logs   - View signing server logs (tail -f)"
	@echo ""
	@echo "Apps:"
	@echo "  test-app              - Build the test app"
	@echo "  example-app           - Build the example app"
	@echo "  run-test-app          - Install and run the test app"
	@echo "  run-example-app       - Install and run the example app"
	@echo ""
	@echo "Publishing:"
	@echo "  publish               - Publish library to GitHub packages"
	@echo ""
	@echo "Usage examples:"
	@echo "  make tests            - Run all tests (start server first if testing hardware signing)"
	@echo "  make tests-with-server - Run all tests with automatic server management"
	@echo "  make signing-server-start && make tests && make signing-server-stop"
	@echo ""
	@echo "  help                  - Show this help message"
