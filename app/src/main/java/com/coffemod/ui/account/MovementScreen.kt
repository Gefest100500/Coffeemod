package com.coffemod.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffemod.data.Product
import com.coffemod.data.Repository
import com.coffemod.ui.products.formatQty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementScreen(
    repo: Repository,
    kind: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val title = if (kind == "in") "Приход" else "Расход"
    androidx.activity.compose.BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val products: List<Product> = remember(query, refresh) {
        repo.products(query).filter { it.id != 0L && (if (kind == "out") it.stock > 0 else true) }
    }
    var target by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Найти товар") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    Surface(
                        onClick = { target = product },
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "остаток: ${formatQty(product.stock)} ${product.unit}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    target?.let { product ->
        var qty by remember(product.id) { mutableStateOf("1") }
        var note by remember(product.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("$title: ${product.name}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Количество (${product.unit})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Комментарий") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = qty.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (value > 0) {
                        repo.addMovement(product.id, kind, value, note.trim())
                        refresh++
                        onChanged()
                    }
                    target = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Отмена") } },
        )
    }
}
