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
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.coffemod.data.SessionItemRow
import com.coffemod.ui.products.formatQty
import com.coffemod.ui.scanner.ScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    repo: Repository,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val session = remember { repo.draftSession() ?: run { repo.startSession(); repo.draftSession() } }
    var refresh by remember { mutableIntStateOf(0) }

    androidx.activity.compose.BackHandler { onBack() }
    val counted: List<SessionItemRow> = remember(refresh) { repo.sessionItems(session?.id ?: -1) }
    val products: List<Product> = remember(refresh) { repo.products() }
    val countedMap = remember(counted) { counted.associateBy { it.productId } }

    var target by remember { mutableStateOf<Product?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    if (scanning) {
        ScannerScreen(
            onCode = { code ->
                scanning = false
                val p = repo.productByCode(code)
                if (p != null) target = p
            },
            onBack = { scanning = false },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Инвентаризация", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { scanning = true }) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Сканировать")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { confirmFinish = true },
                    enabled = counted.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Провести (${counted.size} из ${products.size})") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(products, key = { it.id }) { product ->
                val item = countedMap[product.id]
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
                                "учёт: ${formatQty(product.stock)} ${product.unit}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item == null) {
                            Text("не сосчитано", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val diff = item.actual - item.expected
                            Text(
                                "факт: ${formatQty(item.actual)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (diff == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    target?.let { product ->
        val current = countedMap[product.id]?.actual ?: product.stock
        var value by remember(product.id) { mutableStateOf(formatQty(current)) }
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(product.name) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Фактически (${product.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val qty = value.replace(',', '.').toDoubleOrNull()
                    if (qty != null && session != null) {
                        repo.setSessionItem(session.id, product.id, qty)
                        refresh++
                        onChanged()
                    }
                    target = null
                }) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Отмена") } },
        )
    }

    if (confirmFinish && session != null) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Провести инвентаризацию?") },
            text = {
                Text(
                    "Остатки обновятся по факту: ${counted.size} из ${products.size} позиций. " +
                        "Несосчитанные останутся как есть."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.finishSession(session.id)
                    confirmFinish = false
                    onChanged()
                    onBack()
                }) { Text("Провести") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Отмена") } },
        )
    }
}
