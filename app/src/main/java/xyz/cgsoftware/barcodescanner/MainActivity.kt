package xyz.cgsoftware.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.camera.core.Preview as CameraXPreview

private enum class AppScreen {
    Books,
    Scanner,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarcodeScannerTheme {
                var screen by remember { mutableStateOf(AppScreen.Books) }
                var books by remember { mutableStateOf(setOf<Book>()) }
                var scannedIsbns by remember { mutableStateOf(setOf<String>()) }

                val sortedBooks = remember(books) { books.sortedBy { it.title } }
                val removeBook: (Book) -> Unit = { book ->
                    books = books - book
                    scannedIsbns = scannedIsbns - book.isbn13
                }

                when (screen) {
                    AppScreen.Books -> BooksScreen(
                        books = sortedBooks,
                        onRemoveBook = removeBook,
                        onOpenScanner = { screen = AppScreen.Scanner },
                    )
                    AppScreen.Scanner -> ScannerScreen(
                        books = sortedBooks,
                        scannedIsbns = scannedIsbns,
                        onRemoveBook = removeBook,
                        onOpenBooks = { screen = AppScreen.Books },
                        onIsbnScanned = { isbn -> scannedIsbns = scannedIsbns + isbn },
                        onBookFound = { book -> books = books + book },
                    )
                }
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
fun CameraPreview(
    scannedIsbns: Set<String>,
    onIsbnScanned: (String) -> Unit,
    onBookFound: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Log.e("CameraPreview", "Camera permission denied")
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Camera permission is required to scan barcodes.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        }
        return
    }

    val latestScannedIsbns by rememberUpdatedState(scannedIsbns)
    val latestOnIsbnScanned by rememberUpdatedState(onIsbnScanned)
    val latestOnBookFound by rememberUpdatedState(onBookFound)

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
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(cameraProviderFuture, hasCameraPermission) {
        Log.d("CameraPreview", "LaunchedEffect called")
        val cameraProvider = cameraProviderFuture.get()
        cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        cameraController.imageAnalysisBackgroundExecutor = executor
        cameraController.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(scanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor
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

                    if (latestScannedIsbns.contains(value)) {
                        Log.d("CameraPreview", "Duplicate barcode detected: $value")
                        continue
                    } else {
                        latestOnIsbnScanned(value)
                        val requestBuilder = cronetEngine.newUrlRequestBuilder(
                            "https://www.googleapis.com/books/v1/volumes?q=isbn:$value",
                            BooksApiRequestCallback { book ->
                                mainExecutor.execute {
                                    Log.d("CameraPreview", "Book found: $value")
                                    latestOnBookFound(book)
                                }
                            },
                            executor
                        )

                        requestBuilder.build().start()
                    }
                    }
            }
        )
        previewView.controller = cameraController
        CameraXPreview.Builder().build().also {
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

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}