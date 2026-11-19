package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme

@Composable
fun URLListEditor(
    urls: Set<String>,
    onUrlsChange: (Set<String>) -> Unit
) {
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputSource by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // List of URLs
        LazyColumn(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .border(1.dp, color = MaterialTheme.colorScheme.border),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(20.dp, 10.dp),
        ) {
            itemsIndexed(urls.toList()) { _, url ->
                androidx.tv.material3.ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    selected = selectedItems.contains(url),
                    onClick = {
                        selectedItems = if (selectedItems.contains(url)) {
                            selectedItems - url
                        } else {
                            selectedItems + url
                        }
                    },
                    headlineContent = {
                        Row {
                            Checkbox(checked = selectedItems.contains(url), onCheckedChange = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = url, maxLines = 1)
                        }
                    }
                )
            }
        }

        if (showDialog) {
            AlertDialog(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.material3.MaterialTheme.shapes.extraSmall,
                title = { Text("Input URL") },
                onDismissRequest = { showDialog = false },
                text = {
                    TextField(
                        value = inputSource,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { inputSource = it }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        onUrlsChange(urls + inputSource)
                        showDialog = false
                        inputSource = ""
                    }) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Row {
            IconButton(onClick = {
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
            IconButton(onClick = {
                onUrlsChange(urls - selectedItems)
                selectedItems = emptySet()
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
