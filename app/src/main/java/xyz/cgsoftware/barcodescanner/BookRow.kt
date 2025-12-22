package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.ui.theme.Typography

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BookRow(
    book: Book,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDismiss: (book: Book) -> Unit = {}
) {
    val content = @Composable {
        Row(modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        onLongClick()
                    }
                }
            )
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            if (book.thumbnail != null) {
                AsyncImage(
                    model = book.thumbnail,
                    contentDescription = "Thumbnail for ${book.title}",
                    modifier = Modifier
                        .height(80.dp)
                        .width(80.dp).wrapContentSize(Alignment.Center)
                )
            } else {
                Icon(Icons.Default.BrokenImage,
                    contentDescription = "Thumbnail not found",
                    modifier = Modifier.height(80.dp).width(80.dp).background(Color.LightGray).wrapContentSize(Alignment.Center),
                    tint = Color.White)
            }
            
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            ) {
                Text(
                    book.title,
                    style = Typography.bodyLarge
                )
                if (book.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        book.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(tag.name, style = Typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isSelectionMode) {
        content()
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it == SwipeToDismissBoxValue.StartToEnd) {
                    onDismiss(book)
                }
                false
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.fillMaxHeight()
                                .width(80.dp)
                                .background(Color.Red)
                                .wrapContentSize(Alignment.Center)
                                .padding(12.dp),
                            tint = Color.White
                        )
                    }
                    else -> {}
                }
            }
        ) {
            content()
        }
    }
}
