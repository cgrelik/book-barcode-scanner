package xyz.cgsoftware.barcodescanner.services

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import xyz.cgsoftware.barcodescanner.BuildConfig

private const val TAG = "AuthService"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")
private val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
private val USER_NAME_KEY = stringPreferencesKey("user_name")

class AuthService(private val context: Context) {
    private val dataStore: DataStore<Preferences> = context.dataStore
    private val credentialManager: CredentialManager = CredentialManager.create(context)
    
    /**
     * Create a GetCredentialRequest for Google Sign-In
     * @param filterByAuthorizedAccounts If true, only show previously authorized accounts. If false, show all Google accounts.
     */
    fun createSignInRequest(filterByAuthorizedAccounts: Boolean = true): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .setNonce("thisisthebarcodescannerapp")
            .build()
        
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    /**
     * Sign in with Google using Credential Manager
     * @param activity The activity context needed for the credential request
     * @return The ID token if successful, null otherwise
     */
    suspend fun signIn(activity: Activity): String? {
        return try {
            val request = createSignInRequest(filterByAuthorizedAccounts = true)
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )
            handleSignIn(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Sign-in failed", e)
            null
        }
    }

    /**
     * Sign up with Google (allows new accounts)
     * @param activity The activity context needed for the credential request
     * @return The ID token if successful, null otherwise
     */
    suspend fun signUp(activity: Activity): String? {
        return try {
            val request = createSignInRequest(filterByAuthorizedAccounts = false)
            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )
            handleSignIn(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Sign-up failed", e)
            null
        }
    }

    /**
     * Handle the sign-in result and extract ID token
     */
    private fun handleSignIn(result: GetCredentialResponse): String? {
        return try {
            val credential = result.credential
            
            when (credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val googleIdTokenCredential = GoogleIdTokenCredential
                                .createFrom(credential.data)
                            val idToken = googleIdTokenCredential.idToken
                            if (idToken != null) {
                                Log.d(TAG, "Sign-in successful, ID token obtained")
                                idToken
                            } else {
                                Log.e(TAG, "ID token is null")
                                null
                            }
                        } catch (e: GoogleIdTokenParsingException) {
                            Log.e(TAG, "Received an invalid google id token response", e)
                            null
                        }
                    } else {
                        Log.e(TAG, "Unexpected type of credential: ${credential.type}")
                        null
                    }
                }
                else -> {
                    Log.e(TAG, "Unexpected type of credential: ${credential::class.java.simpleName}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sign-in result", e)
            null
        }
    }

    /**
     * Store JWT token and user info securely
     */
    suspend fun saveToken(token: String, email: String, name: String) {
        dataStore.edit { preferences ->
            preferences[JWT_TOKEN_KEY] = token
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_NAME_KEY] = name
        }
        Log.d(TAG, "Token saved successfully")
    }

    /**
     * Get the stored JWT token
     */
    suspend fun getToken(): String? {
        return dataStore.data.map { preferences ->
            preferences[JWT_TOKEN_KEY]
        }.first()
    }

    /**
     * Get user email
     */
    suspend fun getUserEmail(): String? {
        return dataStore.data.map { preferences ->
            preferences[USER_EMAIL_KEY]
        }.first()
    }

    /**
     * Get user name
     */
    suspend fun getUserName(): String? {
        return dataStore.data.map { preferences ->
            preferences[USER_NAME_KEY] ?: ""
        }.first()
    }

    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        val token = getToken()
        return token != null && token.isNotEmpty()
    }

    /**
     * Sign out and clear stored data
     */
    suspend fun signOut() {
        try {
            // Clear credential state from all credential providers
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing credential state", e)
        }
        
        // Clear local stored data
        dataStore.edit { preferences ->
            preferences.remove(JWT_TOKEN_KEY)
            preferences.remove(USER_EMAIL_KEY)
            preferences.remove(USER_NAME_KEY)
        }
        Log.d(TAG, "Signed out successfully")
    }

    /**
     * Clear only the JWT token (for token refresh scenarios)
     */
    suspend fun clearToken() {
        dataStore.edit { preferences ->
            preferences.remove(JWT_TOKEN_KEY)
        }
    }
}

