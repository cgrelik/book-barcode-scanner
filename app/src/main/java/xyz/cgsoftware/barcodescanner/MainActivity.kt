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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import org.json.JSONArray
import org.json.JSONObject
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.services.AuthService
import xyz.cgsoftware.barcodescanner.services.BackendApi
import xyz.cgsoftware.barcodescanner.ui.LoginScreen
import xyz.cgsoftware.barcodescanner.ui.theme.BarcodeScannerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                AppContent()
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
fun AppContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    var isAuthenticated by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    // Check authentication state on startup
    LaunchedEffect(Unit) {
        isAuthenticated = authService.isAuthenticated()
    }

    when (isAuthenticated) {
        null -> {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        false -> {
            // Show login screen
            LoginScreen(
                onLoginSuccess = {
                    isAuthenticated = true
                }
            )
        }
        true -> {
            // Show camera preview with sign out option
            CameraPreviewWithAuth(
                authService = authService,
                onSignOut = {
                    scope.launch {
                        authService.signOut()
                        isAuthenticated = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewWithAuth(
    authService: AuthService,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
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
    val backendApi = remember { 
        BackendApi(authService, activity).also { it.setActivity(activity) }
    }
    val scope = rememberCoroutineScope()
    
    // Update activity reference if it changes
    LaunchedEffect(activity) {
        backendApi.setActivity(activity)
    }

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
                    if (value == null) {
                        Log.d("CameraPreview", "No value found for barcode")
                        continue
                    }

                    val valid = value.let { validChecksum13(it) } ?: false
                    if (!valid) {
                        Log.d("CameraPreview", "Invalid barcode detected: $value")
                        continue
                    }

                    if (isbns.contains(value)) {
                        Log.d("CameraPreview", "Duplicate barcode detected: $value")
                        continue
                    } else {
                        isbns = isbns.plus(value)
                        val scannedIsbn = value  // Store the scanned ISBN value
                        
                        // Check if book exists via backend API
                        scope.launch {
                            try {
                                val result = backendApi.getBookByIsbn(scannedIsbn)
                                result.onSuccess { responseBody ->
                                    Log.d("CameraPreview", "Book found via backend: $scannedIsbn")
                                    try {
                                        val jsonResponse = JSONObject(responseBody)
                                        val bookId = jsonResponse.getString("id")
                                        val title = jsonResponse.getString("title")
                                        val isbn13 = if (jsonResponse.has("isbn13") && !jsonResponse.isNull("isbn13")) {
                                            jsonResponse.getString("isbn13")
                                        } else {
                                            ""
                                        }
                                        val isbn10 = if (jsonResponse.has("isbn10") && !jsonResponse.isNull("isbn10")) {
                                            jsonResponse.getString("isbn10")
                                        } else {
                                            ""
                                        }
                                        val thumbnail = if (jsonResponse.has("thumbnail") && !jsonResponse.isNull("thumbnail")) {
                                            jsonResponse.getString("thumbnail")
                                        } else {
                                            null
                                        }
                                        
                                        val book = Book(
                                            isbn13 = isbn13,
                                            isbn10 = isbn10,
                                            title = title,
                                            thumbnail = thumbnail,
                                            id = bookId
                                        )
                                        
                                        books = books.plus(book)
                                        Log.d("CameraPreview", "Successfully added book from backend: ${book.title}")
                                    } catch (e: Exception) {
                                        Log.e("CameraPreview", "Error parsing getBookByIsbn response", e)
                                    }
                                }.onFailure { exception ->
                                    Log.e("CameraPreview", "Failed to get book from backend for ISBN: $scannedIsbn", exception)
                                }
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Error getting book from backend: $scannedIsbn", e)
                            }
                        }
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
    
    var userName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        userName = authService.getUserName()
    }
    
    // Load user's books on startup
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val result = backendApi.listBooks()
                result.onSuccess { responseBody ->
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val booksArray = jsonResponse.getJSONArray("books")
                        val loadedBooks = mutableSetOf<Book>()
                        val loadedIsbns = mutableSetOf<String>()
                        
                        for (i in 0 until booksArray.length()) {
                            val bookJson = booksArray.getJSONObject(i)
                            val id = bookJson.getString("id")
                            val title = bookJson.getString("title")
                            val isbn = if (bookJson.has("isbn") && !bookJson.isNull("isbn")) {
                                bookJson.getString("isbn")
                            } else {
                                null
                            }
                            val thumbnail = if (bookJson.has("thumbnail") && !bookJson.isNull("thumbnail")) {
                                bookJson.getString("thumbnail")
                            } else {
                                null
                            }
                            
                            if (isbn != null && isbn.isNotEmpty()) {
                                loadedIsbns.add(isbn)
                                // Create Book object with ISBN from backend
                                // We'll use the ISBN as isbn13, and leave isbn10 empty
                                loadedBooks.add(Book(
                                    isbn13 = isbn,
                                    isbn10 = "",
                                    title = title,
                                    thumbnail = thumbnail,
                                    id = id
                                ))
                            }
                        }
                        
                        // Update state with loaded books and ISBNs
                        books = loadedBooks
                        isbns = loadedIsbns
                        Log.d("CameraPreview", "Loaded ${loadedBooks.size} books from backend")
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Error parsing books response", e)
                    }
                }.onFailure { exception ->
                    Log.e("CameraPreview", "Failed to load books from backend", exception)
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error loading books from backend", e)
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding().systemBarsPadding()) {
        // App bar with user info and sign out
        TopAppBar(
            title = { Text("Book Scanner") },
            actions = {
                if (userName != null) {
                    Text(
                        text = userName!!,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onSignOut) {
                    Text("Sign Out")
                }
            }
        )
        
        Column(modifier = modifier.height(400.dp).fillMaxWidth()) {
            AndroidView(
                factory = { previewView },
                modifier = modifier.height(200.dp).fillMaxWidth()
            )
        }
        LazyColumn(modifier = modifier.fillMaxWidth().weight(1.0f).padding(18.dp)) {
            items(books.toTypedArray()) { book ->
                BookRow(book, onDismiss = { dismissedBook ->
                    // Remove from local state immediately for responsive UI
                    books -= dismissedBook
                    // Delete from server
                    if (dismissedBook.id != null) {
                        scope.launch {
                            try {
                                val result = backendApi.deleteBook(dismissedBook.id)
                                result.onSuccess {
                                    Log.d("CameraPreview", "Successfully deleted book from server: ${dismissedBook.title}")
                                }.onFailure { exception ->
                                    Log.e("CameraPreview", "Failed to delete book from server: ${dismissedBook.title}", exception)
                                    // Re-add to local state if deletion failed
                                    books = books.plus(dismissedBook)
                                }
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Error deleting book from server: ${dismissedBook.title}", e)
                                // Re-add to local state if deletion failed
                                books = books.plus(dismissedBook)
                            }
                        }
                    } else {
                        Log.w("CameraPreview", "Cannot delete book without ID: ${dismissedBook.title}")
                    }
                })
            }
        }
    }
}