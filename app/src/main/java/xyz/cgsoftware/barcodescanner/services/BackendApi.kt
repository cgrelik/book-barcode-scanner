package xyz.cgsoftware.barcodescanner.services

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

class BackendApi(private val authService: AuthService) {
    private val gson = Gson()
    private val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    
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
                
                // Handle 401 Unauthorized - token might be expired
                if (response.code == 401) {
                    Log.w(TAG, "Received 401, clearing token")
                    kotlinx.coroutines.runBlocking { authService.clearToken() }
                }
                
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
     * Make an authenticated GET request
     */
    suspend fun get(endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$endpoint"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
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
     * Delete a book from the user's collection
     */
    suspend fun deleteBook(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/books/$bookId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            
            val response = client.newCall(request).execute()
            
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
     * Make an authenticated POST request
     */
    suspend fun post(endpoint: String, body: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$endpoint"
            val requestBody = body.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
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


