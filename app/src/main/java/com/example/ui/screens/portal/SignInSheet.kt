package com.example.ui.screens.portal

import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.UniTrackApplication
import com.example.data.auth.CredentialStore
import com.example.data.auth.LoginResult
import com.example.data.auth.PortalAuth
import com.example.ui.theme.UniTrackTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    onDismiss: () -> Unit,
    onUseWebsiteLogin: () -> Unit,
    viewModel: PortalViewModel,
    prefilledStudentId: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as UniTrackApplication
    val prefsRepo = app.preferencesRepository
    val colors = UniTrackTheme.colors
    val typography = UniTrackTheme.typography

    var studentId by remember { mutableStateOf(prefilledStudentId) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    val autoState by viewModel.autoSignInState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceSheet,
        modifier = modifier.testTag("signin_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sign in to AIUB Portal",
                style = typography.sectionTitle,
                color = colors.textPrimary
            )

            if (inlineError != null) {
                Text(
                    text = inlineError!!,
                    style = typography.meta,
                    color = colors.statusDanger,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = studentId,
                onValueChange = {
                    studentId = it
                    inlineError = null
                },
                label = { Text("Student ID", color = colors.textSecondary) },
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
                    .testTag("signin_id")
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    inlineError = null
                },
                label = { Text("Password", color = colors.textSecondary) },
                singleLine = true,
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
                    .testTag("signin_password")
            )

            when (val state = autoState) {
                is AutoSignInState.Running -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = colors.accentCyan)
                            Text(
                                text = "Reading captcha... (${state.attempt}/${state.max})",
                                style = typography.body,
                                color = colors.textPrimary
                            )
                        }
                        TextButton(
                            onClick = { viewModel.cancelAutoSignIn() },
                            modifier = Modifier.testTag("portal_signin_cancel")
                        ) {
                            Text("Cancel", color = colors.statusDanger)
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (studentId.isBlank() || password.isBlank()) {
                                inlineError = "Please fill in both Student ID and Password."
                                return@Button
                            }
                            inlineError = null
                            viewModel.startAutoSignIn(
                                context = context,
                                studentId = studentId,
                                password = password,
                                isRetry = true,
                                onSuccess = { landingUrl ->
                                    scope.launch {
                                        CredentialStore(context).save(studentId, password)
                                        prefsRepo.updateStudentId(studentId, true)

                                        val cookieManager = CookieManager.getInstance()
                                        val webView = PortalWebViewHolder.getWebView()
                                        for (cookieStr in PortalAuth.cookiesForWebView()) {
                                            cookieManager.setCookie("https://portal.aiub.edu", "$cookieStr; Path=/")
                                        }
                                        if (webView != null) {
                                            cookieManager.setAcceptThirdPartyCookies(webView, true)
                                        }
                                        cookieManager.flush()
                                        webView?.loadUrl(landingUrl)
                                        onDismiss()
                                    }
                                },
                                onWrongCredentials = {
                                    inlineError = "Incorrect Student ID or Password."
                                },
                                onManualRequired = { result ->
                                    inlineError = when (result) {
                                        is LoginResult.Locked -> "Account is locked."
                                        is LoginResult.NetworkError -> "Network error. Check your connection."
                                        else -> "Couldn't read the captcha. Tap Sign in to try again."
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentCyan,
                            contentColor = colors.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("signin_submit")
                    ) {
                        Text("Sign in", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onUseWebsiteLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use website login instead", color = colors.textSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
