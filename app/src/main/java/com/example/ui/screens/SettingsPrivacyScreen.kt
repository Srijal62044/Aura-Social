package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.UserEntity
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraPurple

import com.example.ui.util.LocalizedStrings

@Composable
fun SettingsPrivacyScreen(
    user: UserEntity? = null,
    isDarkTheme: Boolean,
    isAdmin: Boolean,
    currentLanguage: String = "English (US)",
    onBackClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onLanguageChange: (String) -> Unit = {},
    onLogout: () -> Unit,
    onShowFeedback: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // State for interactive settings
    var isPrivateAccount by remember { mutableStateOf(user?.isPrivate ?: false) }
    var isShowActivityStatus by remember { mutableStateOf(true) }
    var isPushNotificationsEnabled by remember { mutableStateOf(true) }
    var isLikeCommentNotifications by remember { mutableStateOf(true) }
    var isMessageNotifications by remember { mutableStateOf(true) }
    var isHighQualityUploads by remember { mutableStateOf(true) }
    var isTwoFactorAuthEnabled by remember { mutableStateOf(false) }

    // Dialog Visibility States
    var showBlockedAccountsDialog by remember { mutableStateOf(false) }
    var showPasswordSecurityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }

    var cacheSizeMb by remember { mutableStateOf("18.4") }

    // Mock blocked accounts list
    var blockedUsersList by remember {
        mutableStateOf(
            listOf(
                "spammer_bot99" to "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200",
                "fake_account_x" to "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=200"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("settings_privacy_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Settings & Privacy",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
        }

        // Admin Dashboard Link if Admin
        if (isAdmin) {
            Button(
                onClick = onOpenAdminDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(48.dp)
                    .testTag("admin_dashboard_launcher_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AuraPink),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Administrator Dashboard", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Preferences & Appearance Section
        SettingsSectionTitle("Preferences & Display")

        SettingsRow(
            icon = Icons.Default.DarkMode,
            title = "Dark Theme",
            subtitle = if (isDarkTheme) "OLED Dark Mode Enabled" else "Light Mode Enabled",
            trailingContent = {
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onToggleTheme() },
                    modifier = Modifier.testTag("theme_toggle_switch")
                )
            }
        )

        SettingsRow(
            icon = Icons.Default.Language,
            title = LocalizedStrings.get("app_language", currentLanguage),
            subtitle = currentLanguage,
            onClick = { showLanguageDialog = true }
        )

        SettingsRow(
            icon = Icons.Default.Hd,
            title = "High Quality Media Uploads",
            subtitle = "Upload photos and videos in max resolution",
            trailingContent = {
                Switch(
                    checked = isHighQualityUploads,
                    onCheckedChange = {
                        isHighQualityUploads = it
                        onShowFeedback(if (it) "High Quality Media Enabled" else "Standard Media Quality Enabled")
                    }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // Privacy Section
        SettingsSectionTitle("Account Privacy & Visibility")

        SettingsRow(
            icon = Icons.Default.Lock,
            title = "Private Account",
            subtitle = "Only approved followers can view your posts and stories",
            trailingContent = {
                Switch(
                    checked = isPrivateAccount,
                    onCheckedChange = {
                        isPrivateAccount = it
                        onShowFeedback(if (it) "Account set to Private 🔒" else "Account set to Public 🌐")
                    }
                )
            }
        )

        SettingsRow(
            icon = Icons.Default.Visibility,
            title = "Show Activity Status",
            subtitle = "Allow people you message to see when you're online",
            trailingContent = {
                Switch(
                    checked = isShowActivityStatus,
                    onCheckedChange = {
                        isShowActivityStatus = it
                        onShowFeedback(if (it) "Activity Status Visible" else "Activity Status Hidden")
                    }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // Notifications Section
        SettingsSectionTitle("Notifications")

        SettingsRow(
            icon = Icons.Default.Notifications,
            title = "Push Notifications",
            subtitle = "Receive alerts for new activity",
            trailingContent = {
                Switch(
                    checked = isPushNotificationsEnabled,
                    onCheckedChange = {
                        isPushNotificationsEnabled = it
                        onShowFeedback(if (it) "Push Notifications Enabled" else "Push Notifications Muted")
                    }
                )
            }
        )

        if (isPushNotificationsEnabled) {
            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Likes & Comments Alerts",
                trailingContent = {
                    Switch(
                        checked = isLikeCommentNotifications,
                        onCheckedChange = { isLikeCommentNotifications = it }
                    )
                }
            )

            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Direct Message Alerts",
                trailingContent = {
                    Switch(
                        checked = isMessageNotifications,
                        onCheckedChange = { isMessageNotifications = it }
                    )
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // Security Section
        SettingsSectionTitle("Security & Moderation")

        SettingsRow(
            icon = Icons.Default.Block,
            title = "Blocked Accounts",
            subtitle = "${blockedUsersList.size} accounts blocked",
            onClick = { showBlockedAccountsDialog = true }
        )

        SettingsRow(
            icon = Icons.Default.Security,
            title = "Password & Security Center",
            subtitle = "Manage active sessions & update password",
            onClick = { showPasswordSecurityDialog = true }
        )

        SettingsRow(
            icon = Icons.Default.Shield,
            title = "Two-Factor Authentication (2FA)",
            subtitle = "Extra layer of account security",
            trailingContent = {
                Switch(
                    checked = isTwoFactorAuthEnabled,
                    onCheckedChange = {
                        isTwoFactorAuthEnabled = it
                        onShowFeedback(if (it) "2FA Enabled for account!" else "2FA Disabled")
                    }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // Storage & Cache Section
        SettingsSectionTitle("Storage & Maintenance")

        SettingsRow(
            icon = Icons.Default.CleaningServices,
            title = "Clear Application Cache",
            subtitle = "Free up memory space (Currently: $cacheSizeMb MB)",
            onClick = {
                cacheSizeMb = "0.0"
                Toast.makeText(context, "App cache cleared successfully! (18.4 MB freed)", Toast.LENGTH_SHORT).show()
                onShowFeedback("Cache cleared successfully!")
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // Account Management
        SettingsSectionTitle("Account Actions")

        OutlinedButton(
            onClick = { showLogoutConfirmation = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(48.dp)
                .testTag("logout_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out of Aura", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        Button(
            onClick = { showDeleteAccountConfirmation = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(48.dp)
                .testTag("delete_account_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Permanently Delete Account", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // --- DIALOG MODALS ---

    // 1. Language Picker Dialog
    if (showLanguageDialog) {
        val languages = listOf("English (US)", "Hindi (हिंदी)", "Hinglish", "Spanish (Español)")
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select App Language", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    languages.forEach { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguageDialog = false
                                    onLanguageChange(lang)
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (currentLanguage == lang),
                                onClick = {
                                    showLanguageDialog = false
                                    onLanguageChange(lang)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // 2. Blocked Accounts Dialog
    if (showBlockedAccountsDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedAccountsDialog = false },
            title = { Text("Blocked Accounts", fontWeight = FontWeight.Bold) },
            text = {
                if (blockedUsersList.isEmpty()) {
                    Text("You have no blocked users.", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        blockedUsersList.forEach { (username, avatar) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = avatar,
                                        contentDescription = username,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(36.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("@$username", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                TextButton(
                                    onClick = {
                                        blockedUsersList = blockedUsersList.filter { it.first != username }
                                        onShowFeedback("Unblocked @$username")
                                    }
                                ) {
                                    Text("Unblock", color = AuraPink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedAccountsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 3. Password & Security Dialog
    if (showPasswordSecurityDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasswordSecurityDialog = false },
            title = { Text("Password & Security", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Active Session: Android Emulator • Online Now", fontSize = 12.sp, color = Color.Gray)
                    HorizontalDivider()
                    Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it },
                        label = { Text("Current Password") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasswordSecurityDialog = false
                        onShowFeedback("Password updated successfully! 🔒")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPink)
                ) {
                    Text("Update Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordSecurityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Logout Confirmation Dialog
    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Log Out?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out of your Aura account?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPink)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Delete Account Confirmation Dialog
    if (showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirmation = false },
            title = { Text("Permanently Delete Account?", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text("This action cannot be undone. All your posts, messages, and account history will be wiped permanently.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirmation = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = AuraPink
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (trailingContent != null) {
            trailingContent()
        }
    }
}
