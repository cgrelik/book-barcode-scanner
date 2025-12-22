package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.models.Tag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun BooksScreen(
    books: List<Book>,
    tags: List<Tag>,
    selectedTagIds: Set<String>,
    onTagSelectionChanged: (Set<String>) -> Unit,
    onUpdateBookTags: (List<Book>, List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onRemoveBook: (Book) -> Unit,
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagsDialogBooks by remember { mutableStateOf(emptyList<Book>()) }
    val uriHandler = LocalUriHandler.current

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedBookIds = emptySet()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tag Filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedTagIds.isEmpty(),
                        onClick = { onTagSelectionChanged(emptySet()) },
                        label = { Text("All") }
                    )
                }
                items(items = tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = selectedTagIds.contains(tag.id),
                        onClick = {
                            if (selectedTagIds.contains(tag.id)) {
                                onTagSelectionChanged(selectedTagIds - tag.id)
                            } else {
                                onTagSelectionChanged(selectedTagIds + tag.id)
                            }
                        },
                        label = { Text(tag.name) }
                    )
                }
            }

            if (books.isEmpty()) {
                Text("No books yet. Use the scanner to add some.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(items = books, key = { it.isbn13 }) { book ->
                        BookRow(
                            book = book,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedBookIds.contains(book.id),
                            onToggleSelection = {
                                if (selectedBookIds.contains(book.id)) {
                                    selectedBookIds = selectedBookIds - book.id
                                    if (selectedBookIds.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                } else {
                                    selectedBookIds = selectedBookIds + book.id
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedBookIds = selectedBookIds + book.id
                            },
                            onClick = {
                                tagsDialogBooks = listOf(book)
                                showTagDialog = true
                            },
                            onDismiss = onRemoveBook
                        )
                    }
                }
            }
        }

        if (isSelectionMode) {
            FloatingActionButton(
                onClick = {
                    tagsDialogBooks = books.filter { selectedBookIds.contains(it.id) }
                    showTagDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Tag Selected")
            }
        }

        if (showTagDialog) {
            val initialTags = remember(tagsDialogBooks) {
                if (tagsDialogBooks.isEmpty()) emptySet()
                else {
                    val first = tagsDialogBooks.first().tags.map { it.name }.toSet()
                    tagsDialogBooks.drop(1).fold(first) { acc, book ->
                        acc.intersect(book.tags.map { it.name }.toSet())
                    }
                }
            }

            TagPickerDialog(
                availableTags = tags,
                initialTagNames = initialTags,
                onApply = { newTagNames ->
                    onUpdateBookTags(tagsDialogBooks, newTagNames)
                    showTagDialog = false
                    isSelectionMode = false
                    selectedBookIds = emptySet()
                },
                onDismiss = { showTagDialog = false }
            )
        }
    }
}

