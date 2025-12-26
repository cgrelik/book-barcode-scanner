package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.models.Tag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    books: List<Book>,
    scannedIsbns: Set<String>,
    availableTags: List<Tag>,
    autoTagNames: Set<String>,
    modifier: Modifier = Modifier,
    onRemoveBook: (Book) -> Unit,
    onIsbnScanned: (String) -> Unit,
    onAutoTagNamesChanged: (Set<String>) -> Unit,
    onLeaveScreen: () -> Unit,
) {
    var showAutoTagDialog by remember { mutableStateOf(false) }

    // Clear "recently scanned" list when navigating away so coming back starts fresh.
    DisposableEffect(Unit) {
        onDispose { onLeaveScreen() }
    }

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
                .height(200.dp).background(MaterialTheme.colorScheme.background),
            scannedIsbns = scannedIsbns,
            onIsbnScanned = onIsbnScanned,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            OutlinedButton(onClick = { showAutoTagDialog = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                ) {
                Text(
                    if (autoTagNames.isEmpty()) "Auto-tag scanned books"
                    else "Auto-tag: ${autoTagNames.sorted().joinToString(", ")}"
                )
            }

            if (autoTagNames.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    autoTagNames.sorted().forEach { name ->
                        SuggestionChip(onClick = { }, label = { Text(name) })
                    }
                }
            }
        }

        // List below camera preview - explicitly positioned to prevent overlap
        if (books.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 16.dp, horizontal = 8.dp),
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

    if (showAutoTagDialog) {
        TagPickerDialog(
            availableTags = availableTags,
            initialTagNames = autoTagNames,
            onApply = { names ->
                onAutoTagNamesChanged(names.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                showAutoTagDialog = false
            },
            onDismiss = { showAutoTagDialog = false },
        )
    }
}

