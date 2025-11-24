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
package org.contentauth.c2pa.exampleapp.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.contentauth.c2pa.exampleapp.data.PreferencesManager
import org.contentauth.c2pa.exampleapp.model.SigningMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(preferencesManager: PreferencesManager, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentSigningMode by
        preferencesManager.signingMode.collectAsState(initial = SigningMode.DEFAULT)
    val remoteUrl by preferencesManager.remoteUrl.collectAsState(initial = null)
    val remoteToken by preferencesManager.remoteToken.collectAsState(initial = null)
    val customCert by preferencesManager.customCertificate.collectAsState(initial = null)
    val customKey by preferencesManager.customPrivateKey.collectAsState(initial = null)

    var showModeDialog by remember { mutableStateOf(false) }
    var showRemoteConfigDialog by remember { mutableStateOf(false) }
    var showCustomCertDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor =
                    MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Signing Mode Section
            item {
                Card(
                    modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Signing Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SigningModeCard(
                            mode = currentSigningMode,
                            onClick = { showModeDialog = true },
                        )

                        // Show configuration status
                        when (currentSigningMode) {
                            SigningMode.KEYSTORE, SigningMode.HARDWARE -> {
                                if (remoteUrl.isNullOrEmpty()) {
                                    ConfigurationWarning(
                                        text =
                                        "Remote server required for certificate enrollment",
                                        onConfigure = { showRemoteConfigDialog = true },
                                    )
                                }
                            }
                            SigningMode.CUSTOM -> {
                                if (customCert.isNullOrEmpty() || customKey.isNullOrEmpty()) {
                                    ConfigurationWarning(
                                        text = "Certificate and private key required",
                                        onConfigure = { showCustomCertDialog = true },
                                    )
                                }
                            }
                            SigningMode.REMOTE -> {
                                if (remoteUrl.isNullOrEmpty()) {
                                    ConfigurationWarning(
                                        text = "Remote server URL required",
                                        onConfigure = { showRemoteConfigDialog = true },
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Configuration Section
            if (currentSigningMode.requiresConfiguration) {
                item {
                    Card(
                        modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            when (currentSigningMode) {
                                SigningMode.KEYSTORE, SigningMode.HARDWARE, SigningMode.REMOTE -> {
                                    ConfigurationItem(
                                        icon = Icons.Filled.Star,
                                        title = "Remote Server",
                                        subtitle = remoteUrl ?: "Not configured",
                                        onClick = { showRemoteConfigDialog = true },
                                    )
                                }
                                SigningMode.CUSTOM -> {
                                    ConfigurationItem(
                                        icon = Icons.Filled.Folder,
                                        title = "Certificates",
                                        subtitle =
                                        if (customCert != null && customKey != null) {
                                            "Configured"
                                        } else {
                                            "Not configured"
                                        },
                                        onClick = { showCustomCertDialog = true },
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // About Section
            item {
                Card(
                    modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "C2PA Android Example",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(text = "Version 1.0.0", style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text =
                            "This app demonstrates the C2PA Android library with multiple signing methods.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showModeDialog) {
        SigningModeDialog(
            currentMode = currentSigningMode,
            onModeSelected = { mode ->
                scope.launch { preferencesManager.setSigningMode(mode) }
                showModeDialog = false
            },
            onDismiss = { showModeDialog = false },
        )
    }

    if (showRemoteConfigDialog) {
        RemoteConfigDialog(
            currentUrl = remoteUrl ?: "",
            currentToken = remoteToken ?: "",
            onSave = { url, token ->
                scope.launch {
                    preferencesManager.setRemoteUrl(url)
                    preferencesManager.setRemoteToken(token)
                }
                showRemoteConfigDialog = false
            },
            onDismiss = { showRemoteConfigDialog = false },
        )
    }

    if (showCustomCertDialog) {
        CustomCertificateDialog(
            preferencesManager = preferencesManager,
            onDismiss = { showCustomCertDialog = false },
        )
    }
}

@Composable
private fun SigningModeCard(mode: SigningMode, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = getIconForMode(mode),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ConfigurationWarning(text: String, onConfigure: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )

            TextButton(onClick = onConfigure) { Text("Configure") }
        }
    }
}

@Composable
private fun ConfigurationItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun SigningModeDialog(currentMode: SigningMode, onModeSelected: (SigningMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Signing Mode") },
        text = {
            LazyColumn {
                items(SigningMode.entries) { mode ->
                    Card(
                        modifier =
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onModeSelected(mode)
                        },
                        colors =
                        if (mode == currentMode) {
                            CardDefaults.cardColors(
                                containerColor =
                                MaterialTheme.colorScheme
                                    .primaryContainer,
                            )
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(imageVector = getIconForMode(mode), contentDescription = null)

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (mode == currentMode) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RemoteConfigDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote Server Configuration") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://signing-server.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bearer Token (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, token) }, enabled = url.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomCertificateDialog(preferencesManager: PreferencesManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var certContent by remember { mutableStateOf("") }
    var keyContent by remember { mutableStateOf("") }

    val certLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    certContent = stream.bufferedReader().readText()
                }
            }
        }

    val keyLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    keyContent = stream.bufferedReader().readText()
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Certificates") },
        text = {
            Column {
                Text(
                    text = "Upload your certificate chain and private key files",
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedCard(
                    modifier =
                    Modifier.fillMaxWidth().clickable {
                        certLauncher.launch("*/*")
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                            if (certContent.isEmpty()) {
                                "Select Certificate (.pem)"
                            } else {
                                "Certificate loaded"
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedCard(
                    modifier =
                    Modifier.fillMaxWidth().clickable { keyLauncher.launch("*/*") },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                            if (keyContent.isEmpty()) {
                                "Select Private Key (.key)"
                            } else {
                                "Private key loaded"
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        preferencesManager.setCustomCertificate(certContent)
                        preferencesManager.setCustomPrivateKey(keyContent)
                        onDismiss()
                    }
                },
                enabled = certContent.isNotEmpty() && keyContent.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun getIconForMode(mode: SigningMode) = when (mode) {
    SigningMode.DEFAULT -> Icons.Filled.CheckCircle
    SigningMode.KEYSTORE -> Icons.Filled.Lock
    SigningMode.HARDWARE -> Icons.Filled.Star
    SigningMode.CUSTOM -> Icons.Filled.Folder
    SigningMode.REMOTE -> Icons.Filled.Settings
}
