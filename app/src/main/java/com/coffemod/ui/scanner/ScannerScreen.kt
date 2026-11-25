package com.coffemod.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onCode: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    androidx.activity.compose.BackHandler { onBack() }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var torch by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    val controller = remember { LifecycleCameraController(context) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("Сканер", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = { torch = !torch }) {
                    Icon(if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, contentDescription = "Фонарик")
                }
            },
        )

        if (!granted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Нужен доступ к камере, чтобы сканировать коды",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Разрешить")
                    }
                }
            }
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
            )

            LaunchedEffect(controller) {
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.bindToLifecycle(lifecycleOwner)
            }

            LaunchedEffect(torch) {
                controller.enableTorch(torch)
            }

            val scanner = remember {
                BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39,
                            Barcode.FORMAT_QR_CODE,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_ITF,
                        )
                        .build()
                )
            }

            var lastCode by remember { mutableStateOf("") }
            var lastAt by remember { mutableStateOf(0L) }

            DisposableEffect(controller) {
                val executor = Executors.newSingleThreadExecutor()
                val main = Handler(Looper.getMainLooper())
                controller.setImageAnalysisAnalyzer(executor) { imageProxy: ImageProxy ->
                    val media = imageProxy.image
                    if (media == null) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }
                    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (value != null) {
                                val now = System.currentTimeMillis()
                                if (value != lastCode || now - lastAt > 2000) {
                                    lastCode = value
                                    lastAt = now
                                    main.post { onCode(value) }
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                onDispose {
                    controller.clearImageAnalysisAnalyzer()
                    executor.shutdown()
                    scanner.close()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(280.dp)
                    .border(3.dp, Color(0xFF4C8DFF), RoundedCornerShape(24.dp)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Наведите камеру на штрихкод или QR", color = Color.White, fontSize = 15.sp)
                Text("Работает без интернета", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                androidx.compose.material3.TextButton(onClick = { showManual = true }) {
                    Text("Ввести код вручную", color = Color(0xFF8FB8FF))
                }
            }
        }
    }

    if (showManual) {
        var manual by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("Код вручную") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Штрихкод или QR") },
                    singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val value = manual.trim()
                    if (value.isNotEmpty()) {
                        showManual = false
                        onCode(value)
                    }
                }) { Text("Найти") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showManual = false }) { Text("Отмена") }
            },
        )
    }
}
