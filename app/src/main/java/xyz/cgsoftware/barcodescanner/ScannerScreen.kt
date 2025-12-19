package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun ScannerScreen(
    books: List<Book>,
    scannedIsbns: Set<String>,
    modifier: Modifier = Modifier,
    onRemoveBook: (Book) -> Unit,
    onIsbnScanned: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.Top,
    ) {
        // Camera preview edge-to-edge (no horizontal padding)
        CameraPreview(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            scannedIsbns = scannedIsbns,
            onIsbnScanned = onIsbnScanned,
        )

        // List below camera preview - explicitly positioned to prevent overlap
        if (books.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = books, key = { it.isbn13 }) { book ->
                    BookRow(book = book, onDismiss = onRemoveBook)
                }
            }
        } else {
            // Spacer to take remaining space when no books
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.weight(1f)
            )
        }
    }
}

