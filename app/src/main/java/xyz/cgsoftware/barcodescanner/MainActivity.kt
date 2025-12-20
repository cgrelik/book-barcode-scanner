package xyz.cgsoftware.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilterNotNull
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.chromium.net.CronetEngine
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.services.BooksApiRequestCallback
import xyz.cgsoftware.barcodescanner.ui.theme.BarcodeScannerTheme
import java.util.concurrent.Executors
import androidx.camera.core.Preview as CameraPreview

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            // Handle permission denial
            Log.e("MainActivity", "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarcodeScannerTheme {
                CameraPreview()
            }
        }

        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i("MainActivity", "Camera permission granted")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

fun validChecksum13(isbn: String): Boolean {
    if (isbn.length != 13) {
        return false
    }
    val digits = isbn.toCharArray(0,13).map { c ->
        c.digitToIntOrNull()
    }.fastFilterNotNull()
    val checksum = digits.last()
    val sum = digits.take(12).reduceIndexed { index, acc, digit -> acc + (digit * (if (index % 2 == 0) 1 else 3)) }
    Log.d("validChecksum13", "sum: $sum")
    Log.d("validChecksum13", "checksum: $checksum")
    Log.d("validChecksum13", "10 - (sum % 10): ${10 - (sum % 10)}")
    return (10 - (sum % 10)) % 10 == checksum
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val cameraController = remember { LifecycleCameraController(context) }
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
        .build()
    }
    val scanner = remember { BarcodeScanning.getClient(scannerOptions) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var books by remember { mutableStateOf(setOf<Book>()) }
    var isbns by remember { mutableStateOf(setOf<String>()) }
    val cronetEngine = remember { CronetEngine.Builder(context).build() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(cameraProviderFuture) {
        Log.d("CameraPreview", "LaunchedEffect called")
        val cameraProvider = cameraProviderFuture.get()
        cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        cameraController.imageAnalysisBackgroundExecutor = executor
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            MlKitAnalyzer(
                listOf(scanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(context)
            ) { result ->
                val barcodeResults = result.getValue(scanner)
                if (barcodeResults == null) {
                    Log.d("CameraPreview", "No barcode found")
                    return@MlKitAnalyzer
                }
                for (barcode in barcodeResults) {
                    val value = barcode.rawValue
                    val valid = value?.let { validChecksum13(it) } ?: false
                    if (!valid) {
                        Log.d("CameraPreview", "Invalid barcode detected: $value")
                        continue
                    }

                    if (isbns.contains(value)) {
                        Log.d("CameraPreview", "Duplicate barcode detected: $value")
                        continue
                    } else {
                        isbns = isbns.plus(value)
                        val requestBuilder = cronetEngine.newUrlRequestBuilder(
                            "https://www.googleapis.com/books/v1/volumes?q=isbn:$value",
                            BooksApiRequestCallback { book ->
                                Log.d("CameraPreview", "Book found: $value")
                                books = books.plus( book )
                                Log.d("CameraPreview", "Barcode detected: $value")
                            },
                            executor
                        )

                        requestBuilder.build().start()
                    }
                    }
            }
        )
        previewView.controller = cameraController
        CameraPreview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraController.cameraSelector = cameraSelector

        try {
            cameraProvider.unbindAll()
            cameraController.bindToLifecycle(lifecycleOwner)
        } catch (exc: Exception) {
            Log.e("CameraPreview", "Use case binding failed", exc)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    Column(modifier = modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding().systemBarsPadding()) {
        Column(modifier = modifier.height(400.dp).fillMaxWidth()) {
            AndroidView(
                factory = { previewView },
                modifier = modifier.height(200.dp).fillMaxWidth()
            )
        }
        LazyColumn(modifier = modifier.fillMaxWidth().weight(1.0f).padding(18.dp)) {
            items(books.toTypedArray()) { book ->
                BookRow(book, onDismiss = { book -> books -= book})
            }
        }
    }
}