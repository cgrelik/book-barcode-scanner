package xyz.cgsoftware.barcodescanner.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.cgsoftware.barcodescanner.services.AuthService
import xyz.cgsoftware.barcodescanner.services.BackendApi

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val authService = remember { AuthService(context) }
    val backendApi = remember { BackendApi(authService) }
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun performSignIn(filterByAuthorizedAccounts: Boolean = true) {
        if (activity == null) {
            errorMessage = "Activity context required for sign-in"
            return
        }
        
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val idToken = if (filterByAuthorizedAccounts) {
                    authService.signIn(activity)
                } else {
                    authService.signUp(activity)
                }
                
                if (idToken != null) {
                    // Exchange ID token for JWT
                    val authResult = backendApi.exchangeIdTokenForJwt(idToken)
                    
                    authResult.fold(
                        onSuccess = { authResponse ->
                            // Save token and user info
                            authService.saveToken(
                                authResponse.token,
                                authResponse.user.email,
                                authResponse.user.name
                            )
                            isLoading = false
                            onLoginSuccess()
                        },
                        onFailure = { exception ->
                            errorMessage = "Authentication failed: ${exception.message}"
                            isLoading = false
                        }
                    )
                } else {
                    // If sign-in with authorized accounts failed, try sign-up
                    if (filterByAuthorizedAccounts) {
                        performSignIn(filterByAuthorizedAccounts = false)
                    } else {
                        errorMessage = "Failed to get ID token from Google"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Sign-in error: ${e.message}"
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Book Barcode Scanner",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Sign in to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Button(
            onClick = {
                performSignIn()
            },
            enabled = !isLoading && activity != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Sign in with Google")
            }
        }
    }
}

