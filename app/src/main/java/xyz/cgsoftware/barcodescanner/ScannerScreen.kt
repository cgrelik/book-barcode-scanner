package xyz.cgsoftware.barcodescanner

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.chromium.net.CronetEngine
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.services.BooksApiRequestCallback
import java.util.concurrent.Executors
import androidx.camera.core.Preview as CameraPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    scannedBooks: Set<Book>,
    scannedIsbns: Set<String>,
    onBookScanned: (Book, String) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    val cronetEngine = remember { CronetEngine.Builder(context).build() }

    LaunchedEffect(cameraProviderFuture) {
        Log.d("ScannerScreen", "LaunchedEffect called")
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
                    Log.d("ScannerScreen", "No barcode found")
                    return@MlKitAnalyzer
                }
                for (barcode in barcodeResults) {
                    val value = barcode.rawValue
                    val valid = value?.let { validChecksum13(it) } ?: false
                    if (!valid) {
                        Log.d("ScannerScreen", "Invalid barcode detected: $value")
                        continue
                    }

                    if (scannedIsbns.contains(value)) {
                        Log.d("ScannerScreen", "Duplicate barcode detected: $value")
                        continue
                    } else {
                        val requestBuilder = cronetEngine.newUrlRequestBuilder(
                            "https://www.googleapis.com/books/v1/volumes?q=isbn:$value",
                            BooksApiRequestCallback { book ->
                                Log.d("ScannerScreen", "Book found: $value")
                                onBookScanned(book, value!!)
                                Log.d("ScannerScreen", "Barcode detected: $value")
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
            Log.e("ScannerScreen", "Use case binding failed", exc)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Books") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to books"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .padding(16.dp)
            ) {
                items(scannedBooks.toList()) { book ->
                    BookRow(book, onDismiss = onDeleteBook)
                }
            }
        }
    }
}
