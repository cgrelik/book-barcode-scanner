package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun AddMissingBookDialog(
    isbn: String,
    onAdd: (title: String, author: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val trimmedTitle = title.trim()
    val trimmedAuthor = author.trim()
    val trimmedDescription = description.trim()
    val canSubmit = trimmedTitle.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add book to database") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ISBN: $isbn")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = false,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(trimmedTitle, trimmedAuthor, trimmedDescription) },
                enabled = canSubmit
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
