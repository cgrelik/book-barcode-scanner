package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.ui.theme.Typography

@Composable
fun BookRow(book: Book, modifier: Modifier = Modifier, onDismiss: (book: Book) -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
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
        Row(modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { uriHandler.openUri("https://amazon.com/dp/${book.isbn10}") }) {
            if (book.thumbnail != null) {
                AsyncImage(
                    model = book.thumbnail,
                    contentDescription = "Thumbnail for ${book.title}",
                    modifier = modifier
                        .height(80.dp)
                        .width(80.dp).wrapContentSize(Alignment.Center)
                )
            } else {
                Icon(Icons.Default.BrokenImage,
                    contentDescription = "Thumbnail not found",
                    modifier = modifier.height(80.dp).width(80.dp).background(Color.LightGray).wrapContentSize(Alignment.Center),
                    tint = Color.White)
            }
            Text(book.title,
                modifier = modifier.padding(12.dp)
                    .align(Alignment.CenterVertically),
                style = Typography.bodyLarge
                )
        }
    }
}
