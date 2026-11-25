package com.coffemod.ui.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffemod.data.Repository
import com.coffemod.ui.products.formatQty
import com.coffemod.util.LabelItem
import com.coffemod.util.buildLabelsPdf
import com.coffemod.util.exportMovementsCsv
import com.coffemod.util.exportProductsCsv
import com.coffemod.util.exportXlsx
import com.coffemod.util.shareFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(repo: Repository, refreshKey: Int, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("hub") }
    var sessionId by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }

    BackHandler(enabled = mode != "hub") {
        mode = if (mode == "session") "history" else "hub"
    }

    if (mode == "import") {
        SalesImportScreen(
            repo = repo,
            onBack = { mode = "hub" },
            onChanged = onChanged,
        )
        return
    }

    if (mode == "session" && sessionId != null) {
        val items = remember(sessionId) { repo.sessionItems(sessionId!!) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Инвентаризация", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = { mode = "history" }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val diffs = items.filter { it.actual != it.expected }
                item {
                    Text(
                        "Позиций: ${items.size} · расхождений: ${diffs.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                items(items, key = { it.productId }) { item ->
                    val diff = item.actual - item.expected
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "учёт: ${formatQty(item.expected)} · факт: ${formatQty(item.actual)} ${item.unit}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (diff != 0.0) {
                                Text(
                                    (if (diff > 0) "+" else "") + formatQty(diff),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (mode == "labels") {
        val products = remember(refreshKey) { repo.products() }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Этикетки A4", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = { mode = "hub" }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = {
                            busy = true
                            val chosen = if (selected.isEmpty()) products else products.filter { it.id in selected }
                            val items = chosen.map { p -> LabelItem(repo.ensureCode(p.id), p.name, p.unit) }
                            if (items.isEmpty()) {
                                Toast.makeText(context, "Нет товаров для этикеток", Toast.LENGTH_SHORT).show()
                            } else {
                                val file = buildLabelsPdf(items, File(context.cacheDir, "exports/labels.pdf"))
                                shareFile(context, file, "application/pdf")
                            }
                            busy = false
                        },
                        enabled = !busy && products.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(if (selected.isEmpty()) "PDF для всех (${products.size})" else "PDF для выбранных (${selected.size})")
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        "Отметь товары или сними выбор — тогда PDF на всю базу. Товарам без штрихкода присвоится внутренний код INT-…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(products, key = { it.id }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = p.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + p.id else selected - p.id
                            },
                        )
                        Column {
                            Text(p.name, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                repo.codesFor(p.id).firstOrNull() ?: "без кода — INT-${p.id}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        return
    }

    if (mode == "history") {
        val sessions = remember(refreshKey) { repo.sessionsDone() }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("История", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = { mode = "hub" }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sessions.isEmpty()) {
                    item { Text("Проведённых инвентаризаций пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(sessions, key = { it.id }) { s ->
                    val items = remember(s.id) { repo.sessionItems(s.id) }
                    val diffs = items.count { it.actual != it.expected }
                    Surface(
                        onClick = { sessionId = s.id; mode = "session" },
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.startedAt.take(16).replace('T', ' '), color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "позиций: ${items.size} · расхождений: $diffs",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Отчёты", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HubCard("История инвентаризаций", "что и когда считали", Icons.Outlined.History) { mode = "history" }
            HubCard("Этикетки A4", "QR-метки на печать", Icons.Outlined.Print) { mode = "labels" }
            HubCard("Импорт продаж", "CSV из ОФД или кассы", Icons.Outlined.UploadFile) { mode = "import" }
            HubCard("Товары в CSV", "для Excel", Icons.Outlined.FileDownload) {
                val file = exportProductsCsv(context, repo)
                shareFile(context, file, "text/csv")
            }
            HubCard("Движения в CSV", "приход и расход", Icons.Outlined.FileDownload) {
                val file = exportMovementsCsv(context, repo)
                shareFile(context, file, "text/csv")
            }
            HubCard("База в Excel", "почта, MAX, Telegram", Icons.Outlined.Email) {
                val file = exportXlsx(context, repo)
                shareFile(
                    context,
                    file,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "Отправить базу: почта, MAX, Telegram",
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Данные хранятся только на этом устройстве. CSV и PDF уходят через «Поделиться» — оттуда можно распечатать или сохранить.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HubCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
