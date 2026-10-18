package com.seb.sdpr.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seb.sdpr.BuildConfig
import com.seb.sdpr.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    canRefresh: Boolean,
    isSyncing: Boolean,
    onFullRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var tokenVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PlexBackground,
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", fontWeight = FontWeight.Medium, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor        = PlexBackground,
                    titleContentColor     = PlexTextPrimary,
                    navigationIconContentColor = PlexTextSecondary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Bloc IP
            SettingsCard {
                SectionLabel("IP du serveur Plex")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = ui.serverIp,
                    onValueChange = viewModel::onIpChange,
                    placeholder = { Text("ex: 192.168.1.100", color = PlexTextHint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = plexTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Port 32400 utilisé automatiquement", fontSize = 11.sp, color = PlexTextHint)
            }

            // Bloc Token
            SettingsCard {
                SectionLabel("Plex Token")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = ui.plexToken,
                    onValueChange = viewModel::onTokenChange,
                    placeholder = { Text("Colle ton token ici", color = PlexTextHint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = PlexTextHint
                            )
                        }
                    },
                    colors = plexTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Trouvable dans Plex Web → Compte → Paramètres → Sécurité", fontSize = 11.sp, color = PlexTextHint)
            }

            // Feedback
            ui.error?.let {
                Text(it, color = ErrorRed, fontSize = 13.sp)
            }
            ui.successMsg?.let {
                Text(it, color = AudioGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            SettingsCard {
                SectionLabel("Bibliotheque locale")
                Spacer(modifier = Modifier.height(10.dp))
                Text("${ui.movieCount} films indexes", fontSize = 14.sp, color = PlexTextPrimary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (ui.lastSyncTime == 0L) "Sync: jamais"
                    else "Sync: ${SimpleDateFormat("d MMM HH:mm", Locale.CANADA_FRENCH).format(Date(ui.lastSyncTime))}",
                    fontSize = 12.sp,
                    color = PlexTextHint
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Le bouton refresh actualise les films nouveaux ou modifiés. Le scan complet relit tous les détails depuis le serveur sauvegardé et prend plus de temps.",
                    fontSize = 12.sp,
                    color = PlexTextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onFullRefresh,
                    enabled = canRefresh && !isSyncing && !ui.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PlexOrange),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isSyncing) "Synchronisation en cours…" else "Forcer un refresh complet")
                }
            }

            // Bouton
            Button(
                onClick = viewModel::testAndSave,
                enabled = !ui.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PlexOrange, contentColor = PlexBackground),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (ui.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PlexBackground, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test en cours…", fontWeight = FontWeight.Medium)
                } else {
                    Text("Tester et sauvegarder", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }
            Text("v${BuildConfig.VERSION_NAME}", modifier = Modifier.fillMaxWidth(), fontSize = 12.sp, color = PlexTextHint)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlexSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PlexTextMuted, letterSpacing = 0.8.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun plexTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor    = PlexBorderLight,
    focusedBorderColor      = PlexOrange,
    unfocusedTextColor      = PlexTextPrimary,
    focusedTextColor        = PlexTextPrimary,
    unfocusedContainerColor = PlexSurface2,
    focusedContainerColor   = PlexSurface2,
    cursorColor             = PlexOrange
)
