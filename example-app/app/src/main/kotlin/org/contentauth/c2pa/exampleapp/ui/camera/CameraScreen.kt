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

package org.contentauth.c2pa.exampleapp.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onOpenWebCheck: () -> Unit,
    onImageCaptured: (Bitmap, Location?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isCapturing by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedLocation by remember { mutableStateOf<Location?>(null) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Request location permission when camera permission is granted
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted && !locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Clean up resources
        }
    }

    when {
        !cameraPermissionState.status.isGranted -> {
            CameraPermissionRequest(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
            )
        }
        previewBitmap != null -> {
            ImagePreview(
                bitmap = previewBitmap!!,
                location = capturedLocation,
                onConfirm = {
                    onImageCaptured(previewBitmap!!, capturedLocation)
                    previewBitmap = null
                    capturedLocation = null
                },
                onRetake = {
                    previewBitmap = null
                    capturedLocation = null
                },
            )
        }
        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onCapture = { bitmap ->
                        scope.launch {
                            isCapturing = true

                            // Try to get location if permission granted
                            if (locationPermissionState.status.isGranted) {
                                try {
                                    capturedLocation = getCurrentLocation(locationClient)
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Failed to get location", e)
                                }
                            }

                            previewBitmap = bitmap
                            isCapturing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Camera controls overlay
                CameraControls(
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToGallery = onNavigateToGallery,
                    onOpenWebCheck = onOpenWebCheck,
                    isCapturing = isCapturing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(lifecycleOwner: LifecycleOwner, onCapture: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()

                val preview =
                    Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                imageCapture =
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Capture button - positioned above the bottom controls bar
        IconButton(
            onClick = {
                imageCapture?.let { capture ->
                    captureImage(capture, context, cameraExecutor, onCapture)
                }
            },
            modifier =
            Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).size(72.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Capture",
                tint = Color.White,
                modifier =
                Modifier.size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(12.dp),
            )
        }
    }
}

private fun captureImage(
    imageCapture: ImageCapture,
    context: Context,
    executor: ExecutorService,
    onCapture: (Bitmap) -> Unit,
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                // Apply rotation if needed
                val rotatedBitmap = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
                image.close()
                onCapture(rotatedBitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraPreview", "Image capture failed", exception)
            }
        },
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: run {
            // Fallback for YUV format
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
}

private fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
    if (angle == 0) return source

    val matrix = android.graphics.Matrix()
    matrix.postRotate(angle.toFloat())

    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

@Composable
private fun CameraControls(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onOpenWebCheck: () -> Unit,
    isCapturing: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gallery button
        IconButton(onClick = onNavigateToGallery) {
            Icon(
                imageVector = Icons.Filled.Photo,
                contentDescription = "Gallery",
                tint = Color.White,
            )
        }

        // Web Check button
        IconButton(onClick = onOpenWebCheck) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = "Check C2PA",
                tint = Color.White,
            )
        }

        // Spacer
        Spacer(modifier = Modifier.weight(1f))

        // Settings button
        IconButton(onClick = onNavigateToSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ImagePreview(bitmap: Bitmap, location: Location?, onConfirm: () -> Unit, onRetake: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // Location indicator
        if (location != null) {
            Card(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%.4f, %.4f".format(location.latitude, location.longitude),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Action buttons
        Row(
            modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(
                onClick = onRetake,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retake")
            }

            Button(
                onClick = onConfirm,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign & Save")
            }
        }
    }
}

@Composable
private fun CameraPermissionRequest(onRequestPermission: () -> Unit, shouldShowRationale: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Camera Permission Required", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text =
            if (shouldShowRationale) {
                "The camera is needed to capture photos for C2PA signing"
            } else {
                "Please grant camera permission to use this app"
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRequestPermission) { Text("Grant Permission") }
    }
}

@SuppressLint("MissingPermission")
private suspend fun getCurrentLocation(locationClient: FusedLocationProviderClient): Location? = try {
    suspendCancellableCoroutine { continuation ->
        val cancellationToken = CancellationTokenSource()
        locationClient
            .getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token,
            )
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { exception ->
                Log.e("CameraScreen", "Failed to get location", exception)
                continuation.resume(null)
            }

        continuation.invokeOnCancellation { cancellationToken.cancel() }
    }
} catch (e: Exception) {
    Log.e("CameraScreen", "Failed to get current location", e)
    null
}
