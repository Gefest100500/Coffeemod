package com.coffemod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coffemod.data.Repository
import com.coffemod.ui.account.AccountingScreen
import com.coffemod.ui.groups.GroupsScreen
import com.coffemod.ui.reports.ReportsScreen
import com.coffemod.ui.products.ProductEditScreen
import com.coffemod.ui.products.ProductsScreen
import com.coffemod.ui.scanner.ScannerScreen
import com.coffemod.ui.theme.CoffeModTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoffeModTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CoffeModApp()
                }
            }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Сканер", Icons.Outlined.QrCodeScanner),
    Tab("Товары", Icons.Outlined.Inventory2),
    Tab("Учёт", Icons.Outlined.SyncAlt),
    Tab("Отчёты", Icons.Outlined.Assessment),
    Tab("Ещё", Icons.Outlined.MoreHoriz),
)

@Composable
fun CoffeModApp() {
    val context = LocalContext.current
    val repo = remember { Repository(context) }

    var tab by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var editProductId by remember { mutableStateOf<Long?>(null) }
    var showGroups by remember { mutableStateOf(false) }
    var scannedCode by remember { mutableStateOf<String?>(null) }
    var scannerOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tabItem ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            scannerOpen = index == 0
                            if (index != 1) {
                                editProductId = null
                                showGroups = false
                            }
                        },
                        icon = { Icon(tabItem.icon, contentDescription = tabItem.title) },
                        label = { Text(tabItem.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> if (scannerOpen) {
                    ScannerScreen(
                        onCode = { code ->
                            val existing = repo.productByCode(code)
                            if (existing != null) {
                                editProductId = existing.id
                            } else {
                                scannedCode = code
                                editProductId = 0L
                            }
                            scannerOpen = false
                            tab = 1
                        },
                        onBack = { scannerOpen = false },
                    )
                } else {
                    StartScreen(
                        productCount = remember(refreshKey) { repo.products().size },
                        onScan = { scannerOpen = true },
                        onProducts = { tab = 1 },
                    )
                }
                1 -> {
                    val editing = editProductId
                    when {
                        editing != null -> ProductEditScreen(
                            repo = repo,
                            productId = editing,
                            initialCode = if (editing == 0L) scannedCode else null,
                            onDone = {
                                editProductId = null
                                scannedCode = null
                                refreshKey++
                            },
                        )
                        showGroups -> GroupsScreen(
                            repo = repo,
                            refreshKey = refreshKey,
                            onBack = { showGroups = false },
                            onChanged = { refreshKey++ },
                        )
                        else -> ProductsScreen(
                            repo = repo,
                            refreshKey = refreshKey,
                            onOpen = { editProductId = it },
                            onAdd = { editProductId = 0L },
                            onGroups = { showGroups = true },
                        )
                    }
                }
                else -> when (tab) {
                    2 -> AccountingScreen(
                        repo = repo,
                        refreshKey = refreshKey,
                        onChanged = { refreshKey++ },
                    )
                    3 -> ReportsScreen(repo = repo, refreshKey = refreshKey, onChanged = { refreshKey++ })
                    else -> PlaceholderScreen(tabs[tab].title)
                }
            }
        }
    }
}

@Composable
private fun StartScreen(productCount: Int, onScan: () -> Unit, onProducts: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("CM", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
        Text("CoffeMod", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Инвентаризация кофейни", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(40.dp))

        BigActionCard(
            title = "Сканировать",
            subtitle = "Штрихкод или QR — сразу в учёт",
            icon = Icons.Outlined.QrCodeScanner,
            onClick = onScan,
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallActionCard(
                "Товары",
                "$productCount в базе",
                Icons.Outlined.Inventory2,
                Modifier.weight(1f),
                onProducts,
            )
            SmallActionCard("Учёт", "Инвентаризация", Icons.Outlined.SyncAlt, Modifier.weight(1f)) {}
        }

        Spacer(Modifier.weight(1f))
        Text(
            "v0.8 · офлайн · без интернета",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BigActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SmallActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title — в разработке", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
