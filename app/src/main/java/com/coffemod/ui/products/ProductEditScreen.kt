package com.coffemod.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffemod.data.Group
import com.coffemod.data.Product
import com.coffemod.data.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val units = listOf("шт", "кг", "г", "л", "мл", "уп")
private val displayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditScreen(
    repo: Repository,
    productId: Long,
    initialCode: String? = null,
    onDone: () -> Unit,
) {
    val groups: List<Group> = remember { repo.groups() }
    val original: Product? = remember(productId) { if (productId == 0L) null else repo.product(productId) }

    androidx.activity.compose.BackHandler { onDone() }

    var name by remember { mutableStateOf(original?.name ?: "") }
    var groupId by remember { mutableStateOf(original?.groupId ?: groups.firstOrNull()?.id) }
    var unit by remember { mutableStateOf(original?.unit ?: "шт") }
    var minStock by remember { mutableStateOf(original?.minStock?.let { formatQty(it) } ?: "0") }
    var expiresOn by remember { mutableStateOf(original?.expiresOn) }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var codes by remember {
        mutableStateOf(if (productId == 0L && !initialCode.isNullOrBlank()) listOf(initialCode) else repo.codesFor(productId))
    }

    var showDate by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun save() {
        if (name.isBlank()) return
        repo.save(
            Product(
                id = productId,
                name = name,
                groupId = groupId,
                unit = unit,
                minStock = minStock.replace(',', '.').toDoubleOrNull() ?: 0.0,
                stock = original?.stock ?: 0.0,
                expiresOn = expiresOn,
                note = note,
            ),
            codes,
        )
        onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (productId == 0L) "Новый товар" else "Товар", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (productId != 0L) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { save() },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Сохранить") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Штрихкоды", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            BarcodeChips(codes = codes, onRemove = { codes = codes - it }, onAdd = { showCodeDialog = true })

            Spacer(Modifier.height(16.dp))
            Text("Группа", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    FilterChip(
                        selected = groupId == group.id,
                        onClick = { groupId = group.id },
                        label = { Text(group.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Единица измерения", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { unit = u },
                        label = { Text(u) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minStock,
                    onValueChange = { minStock = it },
                    label = { Text("Мин. остаток") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = expiresOn?.let { runCatching { LocalDate.parse(it).format(displayDate) }.getOrDefault(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Срок годности") },
                    placeholder = { Text("не указан") },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showDate = true }) { Text("Выбрать дату") }
                if (expiresOn != null) {
                    TextButton(onClick = { expiresOn = null }) { Text("Очистить") }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Заметка") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val initialMillis = expiresOn?.let {
            runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        expiresOn = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC).toString()
                    }
                    showDate = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = state) }
    }

    if (showCodeDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            title = { Text("Добавить штрихкод") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Код") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = code.trim()
                    if (value.isNotEmpty()) codes = codes + value
                    showCodeDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showCodeDialog = false }) { Text("Отмена") } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить товар?") },
            text = { Text("Штрихкоды и движения этого товара тоже удалятся.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteProduct(productId)
                    showDelete = false
                    onDone()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeChips(codes: List<String>, onRemove: (String) -> Unit, onAdd: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.forEach { code ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                    Text(code, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { onRemove(code) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Убрать", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        AssistChip(
            onClick = onAdd,
            label = { Text("Добавить код") },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        )
    }
}
