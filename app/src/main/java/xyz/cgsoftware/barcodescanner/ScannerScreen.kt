package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cgsoftware.barcodescanner.models.Book

@Composable
fun ScannerScreen(
    books: List<Book>,
    scannedIsbns: Set<String>,
    modifier: Modifier = Modifier,
    onRemoveBook: (Book) -> Unit,
    onOpenBooks: () -> Unit,
    onIsbnScanned: (String) -> Unit,
    onBookFound: (Book) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onOpenBooks, modifier = Modifier.fillMaxWidth()) {
            Text("View books")
        }

        CameraPreview(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            scannedIsbns = scannedIsbns,
            onIsbnScanned = onIsbnScanned,
            onBookFound = onBookFound,
        )

        if (books.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(items = books, key = { it.isbn13 }) { book ->
                    BookRow(book = book, onDismiss = onRemoveBook)
                }
            }
        }
    }
}

