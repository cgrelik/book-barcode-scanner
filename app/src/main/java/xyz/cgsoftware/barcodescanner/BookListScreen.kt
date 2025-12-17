package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import xyz.cgsoftware.barcodescanner.models.Book

@Composable
fun BookListScreen(
    books: Set<Book>,
    onNavigateToScanner: () -> Unit,
    onDeleteBook: (Book) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToScanner) {
                Icon(Icons.Default.Add, contentDescription = "Scan Book")
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(books.toList()) { book ->
                BookRow(book = book, onDismiss = onDeleteBook)
            }
        }
    }
}
