package com.denggl2.mason.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.compose.ui.viewinterop.AndroidView
import com.denggl2.mason.protocol.PairingBootstrap
import com.denggl2.mason.sync.remote.PairedConnector
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingScreen(
    onBack: () -> Unit,
    viewModel: DevicePairingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备扫码配对") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            DevicePairingUiState.Scanning -> QrScanner(
                onQrCode = viewModel::acceptScan,
                modifier = Modifier.padding(padding),
            )
            is DevicePairingUiState.Confirming -> PairingConfirmation(
                bootstrap = state.bootstrap,
                onConfirm = viewModel::confirmPairing,
                onScanAgain = viewModel::scanAgain,
                modifier = Modifier.padding(padding),
            )
            is DevicePairingUiState.Pairing -> PairingProgress(
                endpoint = state.bootstrap.endpoint,
                modifier = Modifier.padding(padding),
            )
            is DevicePairingUiState.Paired -> PairingSuccess(
                connector = state.connector,
                onDone = onBack,
                modifier = Modifier.padding(padding),
            )
            is DevicePairingUiState.Error -> PairingError(
                message = state.message,
                onScanAgain = viewModel::scanAgain,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun QrScanner(
    onQrCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!cameraGranted) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text("需要相机权限才能扫描电脑上的配对二维码")
            Spacer(Modifier.height(20.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("允许使用相机")
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraQrPreview(
            onQrCode = onQrCode,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(244.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(8.dp),
                ),
        )
        Text(
            text = "将电脑上的配对二维码放入框内",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CameraQrPreview(
    onQrCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCode by rememberUpdatedState(onQrCode)
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { QrCodeAnalyzer { currentOnQrCode(it) } }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                cameraProviderFuture.addListener(
                    {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analyzerExecutor, analyzer) }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    },
                    ContextCompat.getMainExecutor(viewContext),
                )
            }
        },
    )

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider?.unbindAll()
            analyzer.close()
            analyzerExecutor.shutdown()
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
private class QrCodeAnalyzer(
    private val onQrCode: (String) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onQrCode)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() {
        scanner.close()
    }
}

@Composable
private fun PairingConfirmation(
    bootstrap: PairingBootstrap,
    onConfirm: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PairingBody(modifier) {
        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(20.dp))
        Text("确认连接这台电脑", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(28.dp))
        PairingDetail("地址", bootstrap.endpoint)
        PairingDetail("设备 ID", bootstrap.offer.connectorDeviceId)
        PairingDetail("证书指纹", bootstrap.tlsCertificateSha256.chunked(8).joinToString(" "))
        Spacer(Modifier.height(30.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text("确认配对")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重新扫描")
        }
    }
}

@Composable
private fun PairingProgress(endpoint: String, modifier: Modifier = Modifier) {
    PairingBody(modifier) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text("正在安全连接", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(endpoint, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PairingSuccess(
    connector: PairedConnector,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PairingBody(modifier) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text("设备已配对", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text(connector.endpoint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
    }
}

@Composable
private fun PairingError(
    message: String,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PairingBody(modifier) {
        Text("无法完成配对", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重新扫描")
        }
    }
}

@Composable
private fun PairingBody(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun PairingDetail(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
