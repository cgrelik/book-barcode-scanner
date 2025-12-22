package xyz.cgsoftware.barcodescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import xyz.cgsoftware.barcodescanner.models.Tag

@Composable
fun ProfileScreen(
    tags: List<Tag>,
    defaultTagIds: Set<String>,
    onUpdateDefaultTags: (List<String>) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Profile",
            modifier = Modifier.padding(vertical = 32.dp)
        )
        
        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Default Tags")
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Out")
        }
    }

    if (showDialog) {
        val initialNames = remember(defaultTagIds, tags) {
            defaultTagIds.mapNotNull { id -> tags.find { it.id == id }?.name }.toSet()
        }

        TagPickerDialog(
            availableTags = tags,
            initialTagNames = initialNames,
            onApply = { names ->
                onUpdateDefaultTags(names)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

