package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.WebServiceTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for web service operations. */
@RunWith(AndroidJUnit4::class)
class AndroidWebServiceTests : WebServiceTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestWebServiceSigningAndVerification() = runBlocking {
        val result = testWebServiceSigningAndVerification()
        // If skipped, that's OK
        if (result.status == TestStatus.SKIPPED) {
            println("Test skipped: ${result.message}")
        } else {
            assertTrue(
                result.success,
                "Web Service Signing & Verification test failed: ${result.message}",
            )
        }
    }

    @Test
    fun runTestWebServiceSignerCreation() = runBlocking {
        val result = testWebServiceSignerCreation()
        // If skipped, that's OK
        if (result.status == TestStatus.SKIPPED) {
            println("Test skipped: ${result.message}")
        } else {
            assertTrue(result.success, "Web Service Signer Creation test failed: ${result.message}")
        }
    }

    @Test
    fun runTestCSRSigning() = runBlocking {
        val result = testCSRSigning()
        // If skipped, that's OK
        if (result.status == TestStatus.SKIPPED) {
            println("Test skipped: ${result.message}")
        } else {
            assertTrue(result.success, "CSR Signing test failed: ${result.message}")
        }
    }
}
