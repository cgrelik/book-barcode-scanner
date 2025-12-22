package xyz.cgsoftware.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilterNotNull
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.services.BackendApiService
import xyz.cgsoftware.barcodescanner.services.TokenStorage
import xyz.cgsoftware.barcodescanner.ui.theme.BarcodeScannerTheme
import java.util.concurrent.Executors
import androidx.camera.core.Preview as CameraXPreview

object Routes {
    const val LOGIN = "login"
    const val BOOKS = "books"
    const val SCANNER = "scanner"
    const val PROFILE = "profile"
}

data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarcodeScannerTheme {
                val context = LocalContext.current
                val tokenStorage = remember { TokenStorage(context) }
                val apiService = remember { BackendApiService(tokenStorage) }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: Routes.LOGIN
                val coroutineScope = rememberCoroutineScope()
                
                var books by remember { mutableStateOf(setOf<Book>()) }
                var scannedIsbns by remember { mutableStateOf(setOf<String>()) }
                var isLoggedIn by remember { mutableStateOf(tokenStorage.hasToken()) }

                val sortedBooks = remember(books) { books.sortedBy { it.title } }
                
                // Google Sign-In setup
                val googleSignInClient = remember {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                        .requestEmail()
                        .build()
                    GoogleSignIn.getClient(context, gso)
                }

                val signInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        try {
                            val account = task.getResult(ApiException::class.java)
                            val idToken = account?.idToken
                            if (idToken != null) {
                                val token: String = idToken
                                apiService.authenticateWithGoogle(token) { authResult ->
                                    authResult.onSuccess {
                                        // Callback is already on main thread, just update state
                                        isLoggedIn = true
                                        // Fetch user's books after login
                                        apiService.getUserBooks { booksResult ->
                                            booksResult.onSuccess { fetchedBooks ->
                                                // Callback is already on main thread
                                                books = fetchedBooks.toSet()
                                                scannedIsbns = fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }.toSet()
                                            }
                                        }
                                    }.onFailure { e ->
                                        Log.e("MainActivity", "Authentication failed", e)
                                    }
                                }
                            }
                        } catch (e: ApiException) {
                            Log.e("MainActivity", "Sign in failed", e)
                        }
                    }
                }

                val removeBook: (Book) -> Unit = { book ->
                    if (book.id.isNotEmpty()) {
                        // Remove from backend
                        apiService.removeBook(book.id) { result ->
                            result.onSuccess {
                                // Remove from local state
                                books = books - book
                                scannedIsbns = scannedIsbns - book.isbn13
                            }.onFailure { e ->
                                Log.e("MainActivity", "Failed to remove book from backend", e)
                                // Still remove from local state for better UX
                                books = books - book
                                scannedIsbns = scannedIsbns - book.isbn13
                            }
                        }
                    } else {
                        // Fallback for books without ID
                        books = books - book
                        scannedIsbns = scannedIsbns - book.isbn13
                    }
                }

                // Fetch books on login
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn && books.isEmpty()) {
                        apiService.getUserBooks { result ->
                            result.onSuccess { fetchedBooks ->
                                books = fetchedBooks.toSet()
                                scannedIsbns = fetchedBooks.map { it.isbn13 }.filter { it.isNotEmpty() }.toSet()
                            }.onFailure { e ->
                                Log.e("MainActivity", "Failed to fetch books", e)
                            }
                        }
                    }
                }

                val destinations = listOf(
                    Destination(Routes.BOOKS, "Books", Icons.Default.Book),
                    Destination(Routes.SCANNER, "Scanner", Icons.Default.CameraAlt),
                    Destination(Routes.PROFILE, "Profile", Icons.Default.Person)
                )
                
                val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }
                    .takeIf { it >= 0 } ?: 0

                if (!isLoggedIn) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Routes.BOOKS)
                            isLoggedIn = true
                        },
                        onSignInClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            signInLauncher.launch(signInIntent)
                        }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                                destinations.forEachIndexed { index, destination ->
                                    NavigationBarItem(
                                        selected = selectedIndex == index,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                destination.icon,
                                                contentDescription = destination.label
                                            )
                                        },
                                        label = { Text(destination.label) }
                                    )
                                }
                            }
                        }
                    ) { contentPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Routes.BOOKS
                        ) {
                            composable(Routes.BOOKS) {
                                BooksScreen(
                                    books = sortedBooks,
                                    onRemoveBook = removeBook,
                                    modifier = Modifier.padding(contentPadding)
                                        .background(MaterialTheme.colorScheme.background)
                                )
                            }
                            composable(Routes.SCANNER) {
                                ScannerScreen(
                                    books = sortedBooks,
                                    scannedIsbns = scannedIsbns,
                                    onRemoveBook = removeBook,
                                    onIsbnScanned = { isbn ->
                                        // Mark as scanned to prevent duplicates
                                        scannedIsbns = scannedIsbns + isbn
                                        // Backend will verify ISBN and add the book
                                        apiService.addBookByIsbn(isbn) { result ->
                                            result.onSuccess { backendBook ->
                                                // Book successfully verified and added to backend
                                                books = books + backendBook
                                                Log.d("MainActivity", "Book verified and added to backend: ${backendBook.title}")
                                            }.onFailure { e ->
                                                Log.e("MainActivity", "Failed to verify/add book to backend: $isbn", e)
                                                // Still mark as scanned to prevent retry spam
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(contentPadding),
                                )
                            }
                            composable(Routes.PROFILE) {
                                ProfileScreen(
                                    onSignOut = {
                                        // Clear token and sign out
                                        tokenStorage.clearToken()
                                        googleSignInClient.signOut()
                                        isLoggedIn = false
                                        books = emptySet()
                                        scannedIsbns = emptySet()
                                    },
                                    modifier = Modifier.padding(contentPadding)
                                        .background(MaterialTheme.colorScheme.background)
                                )
                            }
                        }
                    }
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
                    val value = barcode.rawValue ?: continue
                    val valid = validChecksum13(value)
                    if (!valid) {
                        Log.d("CameraPreview", "Invalid barcode detected: $value")
                        continue
                    }

                    if (latestScannedIsbns.contains(value)) {
                        Log.d("CameraPreview", "Duplicate barcode detected: $value")
                        continue
                    } else {
                        // Mark as scanned immediately to prevent duplicates
                        // The backend will verify the ISBN and add the book
                        latestOnIsbnScanned(value)
                    }
                    }
            }
        )
        previewView.controller = cameraController
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER)
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