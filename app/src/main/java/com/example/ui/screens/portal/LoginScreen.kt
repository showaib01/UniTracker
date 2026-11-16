package com.example.ui.screens.portal

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.auth.CaptchaSolver
import com.example.ui.components.GlassCard
import com.example.ui.theme.UniTrackTheme

@Composable
fun LoginScreen(
    initialStudentId: String,
    initialRememberMe: Boolean,
    isRunning: Boolean,
    progressText: String?,
    errorText: String?,
    captchaBitmap: Bitmap?,
    onLogin: (id: String, pass: String, captchaAns: String, remember: Boolean) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CaptchaSolver.warmUp(context)
    }

    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography

    var studentId by remember(initialStudentId) { mutableStateOf(initialStudentId) }
    var password by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var rememberMe by remember(initialRememberMe) { mutableStateOf(initialRememberMe) }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("login_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.cardFill)
                    .border(2.dp, colors.accentCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_unitrack),
                    contentDescription = "UniTracker Logo",
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = "Sign in to AIUB Portal",
                style = typography.screenTitle,
                color = colors.textPrimary
            )

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText,
                    style = typography.meta,
                    color = colors.statusDanger,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = studentId,
                onValueChange = { studentId = it },
                label = { Text("Student ID", color = colors.textSecondary) },
                singleLine = true,
                enabled = !isRunning,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentCyan,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedLabelColor = colors.accentCyan,
                    unfocusedLabelColor = colors.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_id")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = colors.textSecondary) },
                singleLine = true,
                enabled = !isRunning,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = colors.textSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentCyan,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedLabelColor = colors.accentCyan,
                    unfocusedLabelColor = colors.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password")
            )

            if (captchaBitmap != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Solve Captcha",
                            style = typography.sectionTitle,
                            color = colors.textPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = captchaBitmap.asImageBitmap(),
                                    contentDescription = "Captcha Image",
                                    modifier = Modifier.size(width = 120.dp, height = 48.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            IconButton(
                                onClick = onRefreshCaptcha,
                                modifier = Modifier.background(colors.cardFill, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Captcha",
                                    tint = colors.accentCyan
                                )
                            }
                        }

                        OutlinedTextField(
                            value = captchaAnswer,
                            onValueChange = { captchaAnswer = it },
                            label = { Text("Captcha Answer", color = colors.textSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedLabelColor = colors.accentCyan,
                                unfocusedLabelColor = colors.textSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_captcha_answer")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    enabled = !isRunning
                )
                Text(
                    text = "Save login details on this device",
                    style = typography.body,
                    color = colors.textSecondary
                )
            }

            if (isRunning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = colors.accentCyan, modifier = Modifier.size(24.dp))
                        Text(
                            text = progressText ?: "Signing in...",
                            style = typography.body,
                            color = colors.textPrimary
                        )
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("portal_signin_cancel")
                    ) {
                        Text("Cancel", color = colors.statusDanger)
                    }
                }
            } else {
                Button(
                    onClick = {
                        onLogin(studentId, password, captchaAnswer, rememberMe)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentCyan,
                        contentColor = colors.background
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_submit")
                ) {
                    Text("Log In", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Official Student Portal (portal.aiub.edu)",
                style = typography.meta,
                color = colors.textSecondary
            )
        }
    }
}
