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
package org.contentauth.c2pa.testapp

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.test.shared.BuilderTests
import org.contentauth.c2pa.test.shared.CoreTests
import org.contentauth.c2pa.test.shared.SignerTests
import org.contentauth.c2pa.test.shared.StreamTests
import org.contentauth.c2pa.test.shared.TestBase
import org.contentauth.c2pa.test.shared.WebServiceTests
import java.io.File

// Type alias for convenience
typealias TestResult = TestBase.TestResult

typealias TestStatus = TestBase.TestStatus

/** Helper functions to load resources with common extensions */
private fun loadResourceWithExtensions(resourceName: String): ByteArray? =
    TestBase.loadSharedResourceAsBytes("$resourceName.jpg")
        ?: TestBase.loadSharedResourceAsBytes("$resourceName.pem")
        ?: TestBase.loadSharedResourceAsBytes("$resourceName.key")

private fun loadResourceStringWithExtensions(resourceName: String): String? =
    TestBase.loadSharedResourceAsString("$resourceName.jpg")
        ?: TestBase.loadSharedResourceAsString("$resourceName.pem")
        ?: TestBase.loadSharedResourceAsString("$resourceName.key")

private fun copyResourceToCache(context: Context, resourceName: String, fileName: String): File {
    val file = File(context.cacheDir, fileName)
    val resourceBytes =
        loadResourceWithExtensions(resourceName)
            ?: throw IllegalArgumentException("Resource not found: $resourceName")
    file.writeBytes(resourceBytes)
    return file
}

/** Test runner implementations for the UI */
private class AppCoreTests(private val context: Context) : CoreTests() {
    override fun getContext(): Context = context
    override fun loadResourceAsBytes(resourceName: String): ByteArray = loadResourceWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun loadResourceAsString(resourceName: String): String = loadResourceStringWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        copyResourceToCache(context, resourceName, fileName)
}

private class AppStreamTests(private val context: Context) : StreamTests() {
    override fun getContext(): Context = context
    override fun loadResourceAsBytes(resourceName: String): ByteArray = loadResourceWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun loadResourceAsString(resourceName: String): String = loadResourceStringWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        copyResourceToCache(context, resourceName, fileName)
}

private class AppBuilderTests(private val context: Context) : BuilderTests() {
    override fun getContext(): Context = context
    override fun loadResourceAsBytes(resourceName: String): ByteArray = loadResourceWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun loadResourceAsString(resourceName: String): String = loadResourceStringWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        copyResourceToCache(context, resourceName, fileName)
}

private class AppSignerTests(private val context: Context) : SignerTests() {
    override fun getContext(): Context = context
    override fun loadResourceAsBytes(resourceName: String): ByteArray = loadResourceWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun loadResourceAsString(resourceName: String): String = loadResourceStringWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        copyResourceToCache(context, resourceName, fileName)
}

private class AppWebServiceTests(private val context: Context) : WebServiceTests() {
    override fun getContext(): Context = context
    override fun loadResourceAsBytes(resourceName: String): ByteArray = loadResourceWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun loadResourceAsString(resourceName: String): String = loadResourceStringWithExtensions(resourceName)
        ?: throw IllegalArgumentException("Resource not found: $resourceName")
    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        copyResourceToCache(context, resourceName, fileName)
}

/** Run all tests from all test suites */
private suspend fun runAllTests(context: Context): List<TestResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<TestResult>()

    // Core Tests
    val coreTests = AppCoreTests(context)
    results.add(coreTests.testLibraryVersion())
    results.add(coreTests.testErrorHandling())
    results.add(coreTests.testReadManifestFromTestImage())
    results.add(coreTests.testReadIngredient())
    results.add(coreTests.testInvalidFileHandling())
    results.add(coreTests.testResourceReading())
    results.add(coreTests.testLoadSettings())
    results.add(coreTests.testInvalidInputs())
    results.add(coreTests.testErrorEnumCoverage())

    // Stream Tests
    val streamTests = AppStreamTests(context)
    results.add(streamTests.testStreamOperations())
    results.add(streamTests.testStreamFileOptions())
    results.add(streamTests.testWriteOnlyStreams())
    results.add(streamTests.testCustomStreamCallbacks())
    results.add(streamTests.testFileOperationsWithDataDirectory())

    // Builder Tests
    val builderTests = AppBuilderTests(context)
    results.add(builderTests.testBuilderOperations())
    results.add(builderTests.testBuilderNoEmbed())
    results.add(builderTests.testBuilderRemoteUrl())
    results.add(builderTests.testBuilderAddResource())
    results.add(builderTests.testBuilderAddIngredient())
    results.add(builderTests.testBuilderFromArchive())
    results.add(builderTests.testReaderWithManifestData())
    results.add(builderTests.testJsonRoundTrip())

    // Signer Tests
    val signerTests = AppSignerTests(context)
    results.add(signerTests.testSignerWithCallback())
    results.add(signerTests.testHardwareSignerCreation())
    results.add(signerTests.testStrongBoxSignerCreation())
    results.add(signerTests.testSigningAlgorithms())
    results.add(signerTests.testSignerReserveSize())
    results.add(signerTests.testSignFile())
    results.add(signerTests.testAlgorithmCoverage())

    // Web Service Tests (if server is available)
    val webServiceTests = AppWebServiceTests(context)
    results.add(webServiceTests.testWebServiceSigningAndVerification())
    results.add(webServiceTests.testWebServiceSignerCreation())
    results.add(webServiceTests.testCSRSigning())

    // Additional Core Tests (concurrency, resource error handling)
    results.add(coreTests.testConcurrentOperations())
    results.add(coreTests.testReaderResourceErrorHandling())

    // Additional Stream Tests (large buffer handling)
    results.add(streamTests.testLargeBufferHandling())

    results
}

@Composable
fun TestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var currentTest by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "C2PA Library Tests", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Single button to run all tests
        Button(
            onClick = {
                coroutineScope.launch {
                    isRunning = true
                    testResults = emptyList()
                    currentTest = "Running tests..."
                    testResults = runAllTests(context)
                    currentTest = ""
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running Tests...")
                }
            } else {
                Text("Run All Tests")
            }
        }

        if (currentTest.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentTest,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test summary
        if (testResults.isNotEmpty()) {
            val passed = testResults.count { it.status == TestStatus.PASSED }
            val failed = testResults.count { it.status == TestStatus.FAILED }
            val skipped = testResults.count { it.status == TestStatus.SKIPPED }

            Text(
                text = "Results: $passed passed, $failed failed, $skipped skipped",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Test results list
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            testResults.forEach { result ->
                TestResultCard(result)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TestResultCard(result: TestResult) {
    val backgroundColor =
        when (result.status) {
            TestStatus.PASSED -> Color(0xFFE8F5E9) // Very light green
            TestStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            TestStatus.SKIPPED -> Color(0xFFFFF3E0) // Light amber/yellow
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = result.status.name,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                    when (result.status) {
                        TestStatus.PASSED -> Color(0xFF2E7D32) // Dark green
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        TestStatus.SKIPPED -> Color(0xFFF57C00) // Dark amber
                    },
                )
            }

            if (result.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = result.message, style = MaterialTheme.typography.bodyMedium)
            }

            result.details?.let { details ->
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = details.take(200) + if (details.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
