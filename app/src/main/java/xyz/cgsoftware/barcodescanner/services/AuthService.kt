package xyz.cgsoftware.barcodescanner.services

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import xyz.cgsoftware.barcodescanner.BuildConfig

private const val TAG = "AuthService"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")
private val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
private val USER_NAME_KEY = stringPreferencesKey("user_name")

class AuthService(private val context: Context) {
    private val dataStore: DataStore<Preferences> = context.dataStore
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_OAUTH_CLIENT_ID) // Server client ID for backend verification
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Get the Google Sign-In client for initiating sign-in
     */
    fun getSignInClient(): GoogleSignInClient = googleSignInClient

    /**
     * Handle the sign-in result and extract ID token
     */
    suspend fun handleSignInResult(task: Task<GoogleSignInAccount>): String? {
        return try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                Log.d(TAG, "Sign-in successful, ID token obtained")
                idToken
            } else {
                Log.e(TAG, "ID token is null")
                null
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed: ${e.statusCode}", e)
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
        googleSignInClient.signOut()
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

