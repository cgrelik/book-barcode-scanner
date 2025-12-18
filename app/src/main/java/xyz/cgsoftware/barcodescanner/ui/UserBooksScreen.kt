package xyz.cgsoftware.barcodescanner.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlinx.coroutines.launch
import xyz.cgsoftware.barcodescanner.BookRow
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.services.AuthService
import xyz.cgsoftware.barcodescanner.services.BackendApi

private const val TAG = "UserBooksScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserBooksScreen(
    authService: AuthService,
    backendApi: BackendApi,
    onSignOut: () -> Unit,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var userName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var books by remember { mutableStateOf(setOf<Book>()) }

    LaunchedEffect(Unit) {
        userName = authService.getUserName()
    }

    fun parseBooksResponse(responseBody: String): Set<Book> {
        val jsonResponse = JSONObject(responseBody)
        val booksArray = jsonResponse.getJSONArray("books")
        val loadedBooks = mutableSetOf<Book>()

        for (i in 0 until booksArray.length()) {
            val bookJson = booksArray.getJSONObject(i)
            val id = bookJson.getString("id")
            val title = bookJson.getString("title")
            val isbn = if (bookJson.has("isbn") && !bookJson.isNull("isbn")) {
                bookJson.getString("isbn")
            } else {
                null
            }
            val thumbnail = if (bookJson.has("thumbnail") && !bookJson.isNull("thumbnail")) {
                bookJson.getString("thumbnail")
            } else {
                null
            }

            // Back-end is returning a single `isbn` field; we store it in `isbn13` for now.
            if (!isbn.isNullOrBlank()) {
                loadedBooks.add(
                    Book(
                        isbn13 = isbn,
                        isbn10 = "",
                        title = title,
                        thumbnail = thumbnail,
                        id = id,
                    )
                )
            } else {
                // Still show the book even if ISBN missing, but keep it stable by using ID.
                loadedBooks.add(
                    Book(
                        isbn13 = "missing-isbn-$id",
                        isbn10 = "",
                        title = title,
                        thumbnail = thumbnail,
                        id = id,
                    )
                )
            }
        }

        return loadedBooks
    }

    fun loadBooks() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = backendApi.listBooks()
                result.onSuccess { responseBody ->
                    try {
                        books = parseBooksResponse(responseBody)
                        Log.d(TAG, "Loaded ${books.size} books")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing listBooks response", e)
                        errorMessage = "Could not read books from server"
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to load books", exception)
                    errorMessage = exception.message ?: "Failed to load books"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading books", e)
                errorMessage = e.message ?: "Failed to load books"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadBooks()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .systemBarsPadding()
    ) {
        TopAppBar(
            title = { Text("My Books") },
            actions = {
                if (!userName.isNullOrBlank()) {
                    Text(
                        text = userName!!,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onNavigateToScanner) {
                    Text("Scanner")
                }
                TextButton(onClick = onSignOut) {
                    Text("Sign Out")
                }
            },
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (errorMessage != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { loadBooks() }) {
                    Text("Retry")
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .weight(1f)
        ) {
            items(books.toList()) { book ->
                BookRow(
                    book = book,
                    onDismiss = { dismissedBook ->
                        // Optimistic UI
                        books -= dismissedBook

                        val bookId = dismissedBook.id
                        if (bookId == null) {
                            Log.w(TAG, "Cannot delete book without ID: ${dismissedBook.title}")
                            return@BookRow
                        }

                        scope.launch {
                            try {
                                val result = backendApi.deleteBook(bookId)
                                result.onSuccess {
                                    Log.d(TAG, "Deleted book: ${dismissedBook.title}")
                                }.onFailure { exception ->
                                    Log.e(TAG, "Failed to delete book: ${dismissedBook.title}", exception)
                                    books = books.plus(dismissedBook)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting book: ${dismissedBook.title}", e)
                                books = books.plus(dismissedBook)
                            }
                        }
                    },
                )
            }
        }
    }
}
