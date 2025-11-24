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
package org.contentauth.c2pa.exampleapp.ui.webview

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

class C2PAFileChooserActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val photosDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "C2PA")

                val files = remember {
                    if (photosDir.exists()) {
                        photosDir
                            .listFiles { file ->
                                file.extension.lowercase() in listOf("jpg", "jpeg", "png")
                            }
                            ?.sortedByDescending { it.lastModified() }
                            ?: emptyList()
                    } else {
                        emptyList()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Select C2PA Image") },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        setResult(Activity.RESULT_CANCELED)
                                        finish()
                                    },
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Cancel",
                                    )
                                }
                            },
                        )
                    },
                ) { paddingValues ->
                    if (files.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No C2PA images found",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Take a photo first to create C2PA-signed images",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(files) { file ->
                                ImagePreviewItem(
                                    file = file,
                                    onClick = {
                                        // Create content URI using FileProvider
                                        val uri =
                                            FileProvider.getUriForFile(
                                                context,
                                                "$packageName.fileprovider",
                                                file,
                                            )

                                        // Return the URI to the WebView
                                        val resultIntent =
                                            Intent().apply {
                                                data = uri
                                                addFlags(
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                                )
                                            }
                                        setResult(Activity.RESULT_OK, resultIntent)
                                        finish()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewItem(file: File, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image preview
            AsyncImage(
                model =
                ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Semi-transparent overlay with file name
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = file.nameWithoutExtension,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${(file.length() / 1024)} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
