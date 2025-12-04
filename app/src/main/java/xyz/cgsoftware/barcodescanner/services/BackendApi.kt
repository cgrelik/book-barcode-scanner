package xyz.cgsoftware.barcodescanner.services

import android.app.Activity
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import xyz.cgsoftware.barcodescanner.BuildConfig
import java.io.IOException

private const val TAG = "BackendApi"

data class AuthRequest(
    @SerializedName("id_token")
    val idToken: String
)

data class AuthResponse(
    val token: String,
    val user: UserInfo
)

data class UserInfo(
    val id: String,
    @SerializedName("google_id")
    val googleId: String,
    val email: String,
    val name: String
)

class BackendApi(
    private val authService: AuthService,
    private var activity: Activity? = null
) {
    private val gson = Gson()
    private val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    
    /**
     * Set the Activity reference for re-authentication
     */
    fun setActivity(activity: Activity?) {
        this.activity = activity
    }
    
    /**
     * Attempt to re-authenticate silently using stored credentials
     * Returns true if re-authentication was successful, false otherwise
     */
    private suspend fun attemptReAuthentication(): Boolean {
        val currentActivity = activity
        if (currentActivity == null) {
            Log.w(TAG, "Cannot re-authenticate: Activity is null")
            return false
        }
        
        return try {
            Log.d(TAG, "Attempting silent re-authentication...")
            // Try to get a new ID token silently (with filterByAuthorizedAccounts = true)
            val idToken = authService.signIn(currentActivity)
            
            if (idToken != null) {
                // Exchange ID token for JWT
                val authResult = exchangeIdTokenForJwt(idToken)
                authResult.fold(
                    onSuccess = { authResponse ->
                        // Save new token and user info
                        authService.saveToken(
                            authResponse.token,
                            authResponse.user.email,
                            authResponse.user.name
                        )
                        Log.d(TAG, "Re-authentication successful")
                        true
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Re-authentication failed: ${exception.message}")
                        false
                    }
                )
            } else {
                Log.w(TAG, "Re-authentication failed: Could not get ID token")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during re-authentication", e)
            false
        }
    }
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                // Get token synchronously using runBlocking
                val token = kotlinx.coroutines.runBlocking { authService.getToken() }
                
                val authenticatedRequest = if (token != null) {
                    request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    request
                }
                
                val response = chain.proceed(authenticatedRequest)
                
                // Note: We don't handle 401 here anymore - it's handled at the method level
                // to allow for async re-authentication and retry
                
                response
            }
            .build()
    }

    /**
     * Exchange Google ID token for JWT token
     */
    suspend fun exchangeIdTokenForJwt(idToken: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/auth/google/mobile"
            val requestBody = gson.toJson(AuthRequest(idToken))
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                    Log.d(TAG, "Successfully exchanged ID token for JWT")
                    Result.success(authResponse)
                } else {
                    Log.e(TAG, "Empty response body")
                    Result.failure(IOException("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Failed to exchange token: ${response.code} - $errorBody")
                Result.failure(IOException("Failed to exchange token: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging ID token", e)
            Result.failure(e)
        }
    }

    /**
     * Make an authenticated GET request with automatic re-authentication on 401
     */
    suspend fun get(endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$endpoint"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            var response = client.newCall(request).execute()
            
            // Handle 401 Unauthorized - attempt re-authentication and retry
            if (response.code == 401) {
                Log.w(TAG, "Received 401 for GET $endpoint, attempting re-authentication...")
                response.close() // Close the response before attempting re-auth
                
                // Clear the old token
                authService.clearToken()
                
                // Attempt re-authentication
                if (attemptReAuthentication()) {
                    // Retry the request with new token
                    Log.d(TAG, "Re-authentication successful, retrying GET $endpoint")
                    response = client.newCall(request).execute()
                } else {
                    Log.e(TAG, "Re-authentication failed for GET $endpoint")
                    return@withContext Result.failure(IOException("Authentication failed: Please sign in again"))
                }
            }
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    Result.success(responseBody)
                } else {
                    Result.failure(IOException("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "GET request failed: ${response.code} - $errorBody")
                Result.failure(IOException("Request failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making GET request", e)
            Result.failure(e)
        }
    }

    suspend fun getBookByIsbn(isbn: String): Result<String> {
        return get("/api/books/isbn/$isbn")
    }

    /**
     * Create a book in the backend
     */
    suspend fun createBook(title: String, isbn: String? = null, author: String? = null, description: String? = null, thumbnail: String? = null): Result<String> {
        val requestBodyMap = mutableMapOf<String, Any>(
            "title" to title
        )
        
        // Only include fields if they're not null/empty
        if (!isbn.isNullOrEmpty()) {
            requestBodyMap["isbn"] = isbn
        }
        if (!author.isNullOrEmpty()) {
            requestBodyMap["author"] = author
        }
        if (!description.isNullOrEmpty()) {
            requestBodyMap["description"] = description
        }
        if (!thumbnail.isNullOrEmpty()) {
            requestBodyMap["thumbnail"] = thumbnail
        }
        
        val requestBody = gson.toJson(requestBodyMap)
        return post("/api/books", requestBody)
    }

    /**
     * List all books for the authenticated user
     */
    suspend fun listBooks(): Result<String> {
        return get("/api/books")
    }

    /**
     * Delete a book from the user's collection with automatic re-authentication on 401
     */
    suspend fun deleteBook(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/books/$bookId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            
            var response = client.newCall(request).execute()
            
            // Handle 401 Unauthorized - attempt re-authentication and retry
            if (response.code == 401) {
                Log.w(TAG, "Received 401 for DELETE book $bookId, attempting re-authentication...")
                response.close() // Close the response before attempting re-auth
                
                // Clear the old token
                authService.clearToken()
                
                // Attempt re-authentication
                if (attemptReAuthentication()) {
                    // Retry the request with new token
                    Log.d(TAG, "Re-authentication successful, retrying DELETE book $bookId")
                    response = client.newCall(request).execute()
                } else {
                    Log.e(TAG, "Re-authentication failed for DELETE book $bookId")
                    return@withContext Result.failure(IOException("Authentication failed: Please sign in again"))
                }
            }
            
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully deleted book $bookId")
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "DELETE request failed: ${response.code} - $errorBody")
                Result.failure(IOException("Request failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making DELETE request", e)
            Result.failure(e)
        }
    }

    /**
     * Make an authenticated POST request with automatic re-authentication on 401
     */
    suspend fun post(endpoint: String, body: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$endpoint"
            val requestBody = body.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            var response = client.newCall(request).execute()
            
            // Handle 401 Unauthorized - attempt re-authentication and retry
            if (response.code == 401) {
                Log.w(TAG, "Received 401 for POST $endpoint, attempting re-authentication...")
                response.close() // Close the response before attempting re-auth
                
                // Clear the old token
                authService.clearToken()
                
                // Attempt re-authentication
                if (attemptReAuthentication()) {
                    // Retry the request with new token (need to recreate request body)
                    val retryRequestBody = body.toRequestBody("application/json".toMediaType())
                    val retryRequest = Request.Builder()
                        .url(url)
                        .post(retryRequestBody)
                        .build()
                    Log.d(TAG, "Re-authentication successful, retrying POST $endpoint")
                    response = client.newCall(retryRequest).execute()
                } else {
                    Log.e(TAG, "Re-authentication failed for POST $endpoint")
                    return@withContext Result.failure(IOException("Authentication failed: Please sign in again"))
                }
            }
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    Result.success(responseBody)
                } else {
                    Result.failure(IOException("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "POST request failed: ${response.code} - $errorBody")
                Result.failure(IOException("Request failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making POST request", e)
            Result.failure(e)
        }
    }
}


