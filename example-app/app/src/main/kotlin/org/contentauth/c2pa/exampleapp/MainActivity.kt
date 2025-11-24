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
package org.contentauth.c2pa.exampleapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.contentauth.c2pa.exampleapp.data.C2PAManager
import org.contentauth.c2pa.exampleapp.data.PreferencesManager
import org.contentauth.c2pa.exampleapp.ui.camera.CameraScreen
import org.contentauth.c2pa.exampleapp.ui.gallery.GalleryScreen
import org.contentauth.c2pa.exampleapp.ui.settings.SettingsScreen
import org.contentauth.c2pa.exampleapp.ui.theme.ExampleTheme
import org.contentauth.c2pa.exampleapp.ui.webview.WebViewScreen

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var c2paManager: C2PAManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)
        c2paManager = C2PAManager(this, preferencesManager)

        setContent {
            ExampleTheme {
                C2PAExampleApp(
                    preferencesManager = preferencesManager,
                    c2paManager = c2paManager,
                    onImageSigned = {
                        Toast.makeText(this, "Image signed and saved", Toast.LENGTH_SHORT)
                            .show()
                    },
                )
            }
        }
    }
}

@Composable
fun C2PAExampleApp(preferencesManager: PreferencesManager, c2paManager: C2PAManager, onImageSigned: () -> Unit) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "camera") {
            composable("camera") {
                CameraScreen(
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToGallery = { navController.navigate("gallery") },
                    onOpenWebCheck = { navController.navigate("webview") },
                    onImageCaptured = { bitmap, location ->
                        isProcessing = true
                        processingMessage = "Signing image with C2PA..."

                        // Process in coroutine
                        scope.launch {
                            try {
                                val result = c2paManager.signImage(bitmap, location)
                                result.fold(
                                    onSuccess = { signedBytes ->
                                        processingMessage = "Saving to gallery..."
                                        Log.d(
                                            "MainActivity",
                                            "Signed image size: ${signedBytes.size} bytes",
                                        )

                                        val saveResult =
                                            c2paManager.saveImageToGallery(signedBytes)
                                        saveResult.fold(
                                            onSuccess = { path ->
                                                Log.d(
                                                    "MainActivity",
                                                    "Image saved successfully at: $path",
                                                )
                                                onImageSigned()
                                                // Stay on camera screen after saving
                                            },
                                            onFailure = { error ->
                                                Log.e(
                                                    "MainActivity",
                                                    "Failed to save image",
                                                    error,
                                                )
                                                errorMessage =
                                                    "Failed to save image: ${error.message}"
                                            },
                                        )
                                    },
                                    onFailure = { error ->
                                        Log.e("MainActivity", "Failed to sign image", error)
                                        errorMessage =
                                            "Failed to sign image: ${error.message}"
                                    },
                                )
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Unexpected error", e)
                                errorMessage = "Unexpected error: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                )
            }

            composable("settings") {
                SettingsScreen(
                    preferencesManager = preferencesManager,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable("gallery") {
                GalleryScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("webview") {
                WebViewScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        // Processing overlay
        if (isProcessing) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = processingMessage,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        // Error dialog
        errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) { Text("OK") }
                },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}
