package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.SignerTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for signing operations. */
@RunWith(AndroidJUnit4::class)
class AndroidSignerTests : SignerTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestSignerWithCallback() = runBlocking {
        val result = testSignerWithCallback()
        assertTrue(result.success, "Signer with Callback test failed: ${result.message}")
    }

    @Test
    fun runTestHardwareSignerCreation() = runBlocking {
        val result = testHardwareSignerCreation()
        assertTrue(result.success, "Hardware Signer Creation test failed: ${result.message}")
    }

    @Test
    fun runTestStrongBoxSignerCreation() = runBlocking {
        val result = testStrongBoxSignerCreation()
        assertTrue(result.success, "StrongBox Signer Creation test failed: ${result.message}")
    }

    @Test
    fun runTestSigningAlgorithms() = runBlocking {
        val result = testSigningAlgorithms()
        assertTrue(result.success, "Signing Algorithms test failed: ${result.message}")
    }

    @Test
    fun runTestSignerReserveSize() = runBlocking {
        val result = testSignerReserveSize()
        assertTrue(result.success, "Signer Reserve Size test failed: ${result.message}")
    }

    @Test
    fun runTestSignFile() = runBlocking {
        val result = testSignFile()
        assertTrue(result.success, "Sign File test failed: ${result.message}")
    }

    @Test
    fun runTestAlgorithmCoverage() = runBlocking {
        val result = testAlgorithmCoverage()
        assertTrue(result.success, "Algorithm Coverage test failed: ${result.message}")
    }

    @Test
    fun runTestKeyStoreSignerIntegration() = runBlocking {
        val result = testKeyStoreSignerIntegration()
        assertTrue(result.success, "KeyStore Signer Integration test failed: ${result.message}")
    }

    @Test
    fun runTestStrongBoxSignerIntegration() = runBlocking {
        val result = testStrongBoxSignerIntegration()
        assertTrue(result.success, "StrongBox Signer Integration test failed: ${result.message}")
    }

    @Test
    fun runTestKeyStoreSignerKeyManagement() = runBlocking {
        val result = testKeyStoreSignerKeyManagement()
        assertTrue(result.success, "KeyStore Signer Key Management test failed: ${result.message}")
    }

    @Test
    fun runTestStrongBoxAvailability() = runBlocking {
        val result = testStrongBoxAvailability()
        assertTrue(result.success, "StrongBox Availability test failed: ${result.message}")
    }
}
