package xyz.cgsoftware.barcodescanner.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import xyz.cgsoftware.barcodescanner.BookRow
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.services.AuthService
import xyz.cgsoftware.barcodescanner.services.BackendApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksListView(
    authService: AuthService,
    onSignOut: () -> Unit,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val backendApi = remember { 
        BackendApi(authService, activity).also { it.setActivity(activity) }
    }
    val scope = rememberCoroutineScope()
    
    var books by remember { mutableStateOf(setOf<Book>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Update activity reference if it changes
    LaunchedEffect(activity) {
        backendApi.setActivity(activity)
    }
    
    // Load user's books on startup
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = backendApi.listBooks()
                result.onSuccess { responseBody ->
                    try {
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
                            
                            if (isbn != null && isbn.isNotEmpty()) {
                                // Create Book object with ISBN from backend
                                loadedBooks.add(Book(
                                    isbn13 = isbn,
                                    isbn10 = "",
                                    title = title,
                                    thumbnail = thumbnail,
                                    id = id
                                ))
                            }
                        }
                        
                        books = loadedBooks
                        isLoading = false
                    } catch (e: Exception) {
                        errorMessage = "Error parsing books: ${e.message}"
                        isLoading = false
                    }
                }.onFailure { exception ->
                    errorMessage = "Failed to load books: ${exception.message}"
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMessage = "Error loading books: ${e.message}"
                isLoading = false
            }
        }
    }
    
    var userName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        userName = authService.getUserName()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .systemBarsPadding()
    ) {
        // App bar with user info, scanner button, and sign out
        TopAppBar(
            title = { Text("My Books") },
            actions = {
                Button(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Scanner")
                }
                if (userName != null) {
                    Text(
                        text = userName!!,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onSignOut) {
                    Text("Sign Out")
                }
            }
        )
        
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                val result = backendApi.listBooks()
                                result.onSuccess { responseBody ->
                                    try {
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
                                            
                                            if (isbn != null && isbn.isNotEmpty()) {
                                                loadedBooks.add(Book(
                                                    isbn13 = isbn,
                                                    isbn10 = "",
                                                    title = title,
                                                    thumbnail = thumbnail,
                                                    id = id
                                                ))
                                            }
                                        }
                                        
                                        books = loadedBooks
                                        isLoading = false
                                    } catch (e: Exception) {
                                        errorMessage = "Error parsing books: ${e.message}"
                                        isLoading = false
                                    }
                                }.onFailure { exception ->
                                    errorMessage = "Failed to load books: ${exception.message}"
                                    isLoading = false
                                }
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
            }
            books.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "No books yet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use the scanner to add books to your collection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToScanner) {
                            Text("Go to Scanner")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                        .padding(18.dp)
                ) {
                    items(books.toTypedArray()) { book ->
                        BookRow(book, onDismiss = { dismissedBook ->
                            // Remove from local state immediately for responsive UI
                            books -= dismissedBook
                            // Delete from server
                            if (dismissedBook.id != null) {
                                scope.launch {
                                    try {
                                        val result = backendApi.deleteBook(dismissedBook.id)
                                        result.onSuccess {
                                            // Book deleted successfully
                                        }.onFailure { exception ->
                                            // Re-add to local state if deletion failed
                                            books = books.plus(dismissedBook)
                                        }
                                    } catch (e: Exception) {
                                        // Re-add to local state if deletion failed
                                        books = books.plus(dismissedBook)
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}
