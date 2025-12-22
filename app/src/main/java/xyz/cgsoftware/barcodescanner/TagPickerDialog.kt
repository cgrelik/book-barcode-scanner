package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cgsoftware.barcodescanner.models.Tag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerDialog(
    availableTags: List<Tag>,
    initialTagNames: Set<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedNames by remember { mutableStateOf(initialTagNames) }
    var newTagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("New Tag Name") },
                    singleLine = true,
                    trailingIcon = {
                        if (newTagName.isNotEmpty()) {
                            IconButton(onClick = {
                                if (newTagName.isNotBlank()) {
                                    selectedNames = selectedNames + newTagName.trim()
                                    newTagName = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, "Add Tag")
                            }
                        }
                    }
                )
                
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Existing tags
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = selectedNames.contains(tag.name),
                            onClick = {
                                if (selectedNames.contains(tag.name)) {
                                    selectedNames = selectedNames - tag.name
                                } else {
                                    selectedNames = selectedNames + tag.name
                                }
                            },
                            label = { Text(tag.name) }
                        )
                    }
                    // Newly added tags (not yet in availableTags)
                    selectedNames.filter { name -> availableTags.none { it.name == name } }.forEach { name ->
                         FilterChip(
                            selected = true,
                            onClick = { selectedNames = selectedNames - name },
                            label = { Text(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedNames.toList()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

