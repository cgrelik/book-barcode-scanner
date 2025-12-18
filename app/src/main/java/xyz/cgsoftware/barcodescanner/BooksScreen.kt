package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cgsoftware.barcodescanner.models.Book

@Composable
fun BooksScreen(
    books: List<Book>,
    modifier: Modifier = Modifier,
    onRemoveBook: (Book) -> Unit,
    onOpenScanner: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onOpenScanner, modifier = Modifier.fillMaxWidth()) {
            Text("Scan books")
        }

        if (books.isEmpty()) {
            Text("No books yet. Tap “Scan books” to add some.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(items = books, key = { it.isbn13 }) { book ->
                    BookRow(book = book, onDismiss = onRemoveBook)
                }
            }
        }
    }
}

