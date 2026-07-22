package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuthState
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraPurple

@Composable
fun AuthScreen(
    authState: AuthState,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onSwitchState: (AuthState) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var localError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        AuraPurple.copy(alpha = 0.2f),
                        AuraPink.copy(alpha = 0.15f)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AURA",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        brush = Brush.horizontalGradient(listOf(AuraPink, AuraPurple, AuraOrange))
                    )
                )
                Text(
                    text = when (authState) {
                        AuthState.LOGIN -> "Log in to your account"
                        AuthState.REGISTER -> "Create your new account"
                        AuthState.FORGOT_PASSWORD -> "Reset your password"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Error Banner
                val displayError = localError ?: errorMessage
                if (!displayError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                if (authState == AuthState.REGISTER) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            localError = null
                        },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.lowercase().trim()
                            localError = null
                        },
                        label = { Text("Unique Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_username_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.trim()
                            localError = null
                        },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            localError = null
                        },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .testTag("auth_confirm_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                } else if (authState == AuthState.LOGIN) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            localError = null
                        },
                        label = { Text("Username or Email") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_login_identifier"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .testTag("auth_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                } else if (authState == AuthState.FORGOT_PASSWORD) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            localError = null
                        },
                        label = { Text("Username or Email") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_reset_identifier"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                        },
                        label = { Text("New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_new_password"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            localError = null
                        },
                        label = { Text("Confirm New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .testTag("auth_confirm_new_password"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        localError = null
                        when (authState) {
                            AuthState.LOGIN -> {
                                if (username.isBlank() || password.isBlank()) {
                                    localError = "Please enter both username/email and password."
                                } else {
                                    onLogin(username, password)
                                }
                            }
                            AuthState.REGISTER -> {
                                when {
                                    name.isBlank() -> localError = "Full Name is required."
                                    username.isBlank() -> localError = "Unique Username is required."
                                    username.length < 3 -> localError = "Username must be at least 3 characters."
                                    !username.matches(Regex("^[a-zA-Z0-9_.]+$")) -> localError = "Username can only contain letters, numbers, underscores and dots."
                                    email.isBlank() || !email.contains("@") -> localError = "Please enter a valid email address."
                                    password.isBlank() -> localError = "Password is required."
                                    password.length < 4 -> localError = "Password must be at least 4 characters long."
                                    password != confirmPassword -> localError = "Password and confirm password do not match."
                                    else -> onRegister(name, username, email, password)
                                }
                            }
                            AuthState.FORGOT_PASSWORD -> {
                                when {
                                    username.isBlank() -> localError = "Please enter your username or email."
                                    password.isBlank() -> localError = "New password is required."
                                    password.length < 4 -> localError = "Password must be at least 4 characters."
                                    password != confirmPassword -> localError = "Passwords do not match."
                                    else -> onResetPassword(username, password)
                                }
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_action_button"),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPink)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = when (authState) {
                                AuthState.LOGIN -> "Log In"
                                AuthState.REGISTER -> "Create Account"
                                AuthState.FORGOT_PASSWORD -> "Reset Password"
                                else -> "Continue"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (authState == AuthState.LOGIN) {
                        TextButton(onClick = {
                            localError = null
                            onSwitchState(AuthState.FORGOT_PASSWORD)
                        }) {
                            Text("Forgot Password?", fontSize = 12.sp)
                        }
                        Text("•", color = Color.Gray, modifier = Modifier.padding(horizontal = 4.dp))
                        TextButton(onClick = {
                            localError = null
                            onSwitchState(AuthState.REGISTER)
                        }) {
                            Text("Sign Up", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (authState == AuthState.REGISTER) {
                        Text("Already have an account?", fontSize = 12.sp)
                        TextButton(onClick = {
                            localError = null
                            onSwitchState(AuthState.LOGIN)
                        }) {
                            Text("Log In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = {
                            localError = null
                            onSwitchState(AuthState.LOGIN)
                        }) {
                            Text("Back to Log In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
