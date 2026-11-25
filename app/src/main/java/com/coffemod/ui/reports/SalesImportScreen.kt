package com.coffemod.ui.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffemod.data.Repository
import com.coffemod.ui.products.formatQty
import com.coffemod.util.SaleLine
import com.coffemod.util.SalesImport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesImportScreen(
    repo: Repository,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var bindTarget by remember { mutableStateOf<SaleLine?>(null) }

    BackHandler { onBack() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Не удалось открыть файл")
            fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "файл"
            rows = SalesImport.parse(text, repo)
            error = if (rows.isEmpty()) "Не нашла ни одной строки «дата; товар; количество»" else null
        }.onFailure {
            error = it.message ?: "Ошибка чтения файла"
        }
    }

    val matched = rows.filter { it.product != null }
    val unmatched = rows.filter { it.product == null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Импорт продаж", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = {
                        val count = SalesImport.import(matched, repo, "Импорт продаж: $fileName")
                        onChanged()
                        Toast.makeText(context, "Списано позиций: $count", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    enabled = matched.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Списать ${matched.size} позиций") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedButton(onClick = {
                    picker.launch(arrayOf("text/*", "text/comma-separated-values", "text/csv", "application/vnd.ms-excel", "*/*"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Выбрать файл CSV")
                }
            }
            item {
                Text(
                    "Формат: дата; товар или штрихкод; количество. Разделитель ; или , — определится сам. " +
                        "Строки сопоставляются со штрихкодами и названиями товаров.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (fileName.isNotEmpty()) {
                item {
                    Text("Файл: $fileName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
            if (rows.isNotEmpty()) {
                item {
                    Text(
                        "Строк: ${rows.size} · распознано: ${matched.size} · не найдено: ${unmatched.size}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(matched, key = { it.raw + it.date + it.qty }) { row ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.product?.name ?: row.raw, color = MaterialTheme.colorScheme.onSurface)
                                Text("${row.date} · ${row.raw}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "−" + formatQty(row.qty) + " " + (row.product?.unit ?: ""),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (unmatched.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Не найдены в базе", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(unmatched, key = { "u" + it.raw + it.date }) { row ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.raw, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "${row.date} · ${formatQty(row.qty)} — добавь товар или штрихкод",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { bindTarget = row }) { Text("Привязать") }
                            }
                        }
                    }
                }
            }
        }
    }

    bindTarget?.let { row ->
        var search by remember(row.raw) { mutableStateOf("") }
        val products = remember(search) { repo.products(search) }
        AlertDialog(
            onDismissRequest = { bindTarget = null },
            title = { Text("Привязать: ${row.raw}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Поиск товара") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(products, key = { it.id }) { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(product.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                androidx.compose.material3.TextButton(onClick = {
                                    repo.setAlias(row.raw, product.id)
                                    rows = rows.map {
                                        if (it.raw == row.raw && it.product == null) it.copy(product = product) else it
                                    }
                                    bindTarget = null
                                }) { Text("Выбрать") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { bindTarget = null }) { Text("Отмена") }
            },
        )
    }
}
