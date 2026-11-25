package com.coffemod.ui.account

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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffemod.data.Repository
import com.coffemod.ui.products.formatQty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(
    repo: Repository,
    refreshKey: Int,
    onChanged: () -> Unit,
) {
    var mode by remember { mutableStateOf("hub") }
    var draftId by remember(refreshKey) { mutableStateOf(repo.draftSession()?.id) }

    androidx.activity.compose.BackHandler(enabled = mode != "hub") { mode = "hub" }

    when (mode) {
        "in" -> MovementScreen(
            repo = repo,
            kind = "in",
            onBack = { mode = "hub" },
            onChanged = { onChanged() },
        )
        "out" -> MovementScreen(
            repo = repo,
            kind = "out",
            onBack = { mode = "hub" },
            onChanged = { onChanged() },
        )
        "inventory" -> InventoryScreen(
            repo = repo,
            onBack = { mode = "hub"; draftId = repo.draftSession()?.id },
            onChanged = { onChanged() },
        )
        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Учёт", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            val movements = remember(refreshKey) { repo.movements(20) }
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HubCard("Приход", "поставка", Icons.Outlined.AddCircleOutline, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { mode = "in" }
                        HubCard("Расход", "списание", Icons.Outlined.RemoveCircleOutline, MaterialTheme.colorScheme.error, Modifier.weight(1f)) { mode = "out" }
                    }
                }
                item {
                    HubCard(
                        "Инвентаризация",
                        if (draftId != null) "черновик открыт — продолжить" else "пересчитать полки",
                        Icons.Outlined.FactCheck,
                        MaterialTheme.colorScheme.primary,
                        Modifier.fillMaxWidth(),
                    ) { mode = "inventory" }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Последние движения", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (movements.isEmpty()) {
                    item {
                        Text(
                            "Пока нет ни приходов, ни расходов",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    items(movements, key = { it.id }) { m ->
                        MovementRowView(
                            name = m.productName,
                            kind = m.kind,
                            qty = m.qty,
                            unit = m.unit,
                            date = m.onDate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementRowView(name: String, kind: String, qty: Double, unit: String, date: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                (if (kind == "in") "+" else "−") + formatQty(qty) + " " + unit,
                fontWeight = FontWeight.SemiBold,
                color = if (kind == "in") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
