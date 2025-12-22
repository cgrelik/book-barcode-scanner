package xyz.cgsoftware.barcodescanner.services

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.cgsoftware.barcodescanner.BuildConfig
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.models.Tag
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "BackendApiService"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("user") val user: UserInfo
)

data class UserInfo(
    @SerializedName("id") val id: String,
    @SerializedName("google_id") val googleId: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String
)

data class BooksResponse(
    @SerializedName("books") val books: List<BookResponse>,
    @SerializedName("count") val count: Int
)

data class BookResponse(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("isbn13") val isbn13: String?,
    @SerializedName("isbn10") val isbn10: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("tags") val tags: List<Tag>?
)

fun BookResponse.toBook(): Book {
    return Book(
        id = id,
        isbn13 = isbn13 ?: "",
        isbn10 = isbn10 ?: "",
        title = title,
        thumbnail = thumbnail,
        tags = tags ?: emptyList()
    )
}

data class TagsResponse(
    @SerializedName("tags") val tags: List<Tag>,
    @SerializedName("count") val count: Int
)

data class CreateTagRequest(
    @SerializedName("name") val name: String
)

data class SetBookTagsRequest(
    @SerializedName("tags") val tags: List<String>
)

data class UserPreference(
    @SerializedName("user_id") val userId: String,
    @SerializedName("default_tag_ids") val defaultTagIds: List<String>,
    @SerializedName("updated_at") val updatedAt: String
)

data class UpdatePreferencesRequest(
    @SerializedName("default_tag_ids") val defaultTagIds: List<String>
)

class BackendApiService(private val tokenStorage: TokenStorage) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    private val mainHandler = Handler(Looper.getMainLooper())

    fun authenticateWithGoogle(idToken: String, callback: (Result<AuthResponse>) -> Unit) {
        val requestBody = gson.toJson(mapOf("id_token" to idToken))
            .toRequestBody(JSON_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url("$baseUrl/auth/google/mobile")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Authentication failed", e)
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val authResponse = gson.fromJson(it.body?.string(), AuthResponse::class.java)
                            tokenStorage.saveToken(authResponse.token)
                            mainHandler.post {
                                callback(Result.success(authResponse))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse auth response", e)
                            mainHandler.post {
                                callback(Result.failure(e))
                            }
                        }
                    } else {
                        Log.e(TAG, "Authentication failed with status: ${it.code}")
                        mainHandler.post {
                            callback(Result.failure(IOException("Authentication failed: ${it.code}")))
                        }
                    }
                }
            }
        })
    }

    fun getUserBooks(tagIds: List<String>? = null, callback: (Result<List<Book>>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val urlBuilder = "$baseUrl/api/books".toHttpUrlOrNull()?.newBuilder()
            ?: return callback(Result.failure(IOException("Invalid URL")))

        if (tagIds != null && tagIds.isNotEmpty()) {
            urlBuilder.addQueryParameter("tags", tagIds.joinToString(","))
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch books", e)
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val body = it.body?.string() ?: return@use
                            val booksResponse = gson.fromJson(body, BooksResponse::class.java)
                            val books = booksResponse.books.map { it.toBook() }
                            mainHandler.post {
                                callback(Result.success(books))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse books response", e)
                            mainHandler.post {
                                callback(Result.failure(e))
                            }
                        }
                    } else {
                        if (it.code == 401) {
                            tokenStorage.clearToken()
                        }
                        Log.e(TAG, "Failed to fetch books with status: ${it.code}")
                        mainHandler.post {
                            callback(Result.failure(IOException("Failed to fetch books: ${it.code}")))
                        }
                    }
                }
            }
        })
    }

    fun addBookByIsbn(isbn: String, callback: (Result<Book>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val requestBody = gson.toJson(mapOf("isbn" to isbn))
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl/api/books/add")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to add book", e)
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val body = it.body?.string() ?: return@use
                            val bookResponse = gson.fromJson(body, BookResponse::class.java)
                            mainHandler.post {
                                callback(Result.success(bookResponse.toBook()))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse book response", e)
                            mainHandler.post {
                                callback(Result.failure(e))
                            }
                        }
                    } else {
                        if (it.code == 401) {
                            tokenStorage.clearToken()
                        }
                        Log.e(TAG, "Failed to add book with status: ${it.code}")
                        mainHandler.post {
                            callback(Result.failure(IOException("Failed to add book: ${it.code}")))
                        }
                    }
                }
            }
        })
    }

    fun removeBook(bookId: String, callback: (Result<Unit>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val request = Request.Builder()
            .url("$baseUrl/api/books/$bookId")
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to remove book", e)
                mainHandler.post {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        mainHandler.post {
                            callback(Result.success(Unit))
                        }
                    } else {
                        if (it.code == 401) {
                            tokenStorage.clearToken()
                        }
                        Log.e(TAG, "Failed to remove book with status: ${it.code}")
                        mainHandler.post {
                            callback(Result.failure(IOException("Failed to remove book: ${it.code}")))
                        }
                    }
                }
            }
        })
    }

    fun getTags(callback: (Result<List<Tag>>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch tags", e)
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val body = it.body?.string() ?: return@use
                            val tagsResponse = gson.fromJson(body, TagsResponse::class.java)
                            mainHandler.post { callback(Result.success(tagsResponse.tags)) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse tags response", e)
                            mainHandler.post { callback(Result.failure(e)) }
                        }
                    } else {
                        if (it.code == 401) tokenStorage.clearToken()
                        mainHandler.post { callback(Result.failure(IOException("Failed to fetch tags: ${it.code}"))) }
                    }
                }
            }
        })
    }

    fun createTag(name: String, callback: (Result<Tag>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val requestBody = gson.toJson(CreateTagRequest(name)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val body = it.body?.string() ?: return@use
                            val tag = gson.fromJson(body, Tag::class.java)
                            mainHandler.post { callback(Result.success(tag)) }
                        } catch (e: Exception) {
                            mainHandler.post { callback(Result.failure(e)) }
                        }
                    } else {
                        if (it.code == 401) tokenStorage.clearToken()
                        mainHandler.post { callback(Result.failure(IOException("Failed to create tag: ${it.code}"))) }
                    }
                }
            }
        })
    }

    fun setBookTags(bookId: String, tags: List<String>, callback: (Result<Unit>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val requestBody = gson.toJson(SetBookTagsRequest(tags)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/books/$bookId/tags")
            .put(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        mainHandler.post { callback(Result.success(Unit)) }
                    } else {
                        if (it.code == 401) tokenStorage.clearToken()
                        mainHandler.post { callback(Result.failure(IOException("Failed to set tags: ${it.code}"))) }
                    }
                }
            }
        })
    }

    fun getPreferences(callback: (Result<UserPreference>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val request = Request.Builder()
            .url("$baseUrl/api/user/preferences")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val body = it.body?.string() ?: return@use
                            val pref = gson.fromJson(body, UserPreference::class.java)
                            mainHandler.post { callback(Result.success(pref)) }
                        } catch (e: Exception) {
                            mainHandler.post { callback(Result.failure(e)) }
                        }
                    } else {
                        if (it.code == 401) tokenStorage.clearToken()
                        mainHandler.post { callback(Result.failure(IOException("Failed to fetch preferences: ${it.code}"))) }
                    }
                }
            }
        })
    }

    fun updatePreferences(defaultTagIds: List<String>, callback: (Result<UserPreference>) -> Unit) {
        val token = tokenStorage.getToken()
        if (token == null) {
            callback(Result.failure(IOException("No authentication token")))
            return
        }

        val requestBody = gson.toJson(UpdatePreferencesRequest(defaultTagIds)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/user/preferences")
            .put(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        try {
                            val body = it.body?.string() ?: return@use
                            val pref = gson.fromJson(body, UserPreference::class.java)
                            mainHandler.post { callback(Result.success(pref)) }
                        } catch (e: Exception) {
                            mainHandler.post { callback(Result.failure(e)) }
                        }
                    } else {
                        if (it.code == 401) tokenStorage.clearToken()
                        mainHandler.post { callback(Result.failure(IOException("Failed to update preferences: ${it.code}"))) }
                    }
                }
            }
        })
    }
}

