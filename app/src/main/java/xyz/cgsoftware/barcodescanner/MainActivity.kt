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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilterNotNull
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.models.Tag
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
                var recentScannedBooks by remember { mutableStateOf(listOf<Book>()) }
                var tags by remember { mutableStateOf(emptyList<Tag>()) }
                var selectedTagIds by remember { mutableStateOf(emptySet<String>()) }
                var defaultTagIds by remember { mutableStateOf(emptySet<String>()) }
                var scannerAutoTagNames by remember { mutableStateOf(emptySet<String>()) }
                var isLoggedIn by remember { mutableStateOf(tokenStorage.hasToken()) }

                val sortedBooks = remember(books) { books.sortedBy { it.title } }

                val credentialManager = remember { CredentialManager.create(context) }

                val googleIdOption = remember {
                    GetGoogleIdOption.Builder()
                        .setServerClientId(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
                        .setFilterByAuthorizedAccounts(true)
                        .build()
                }

                val googleSignInRequest = remember {
                    GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                }

                val handleGoogleSignInToken: (String) -> Unit = { token ->
                    apiService.authenticateWithGoogle(token) { authResult ->
                        authResult.onSuccess {
                            navController.navigate(Routes.BOOKS)
                            isLoggedIn = true
                            apiService.getUserBooks { booksResult ->
                                booksResult.onSuccess { fetchedBooks ->
                                    books = fetchedBooks.toSet()
                                    scannedIsbns =
                                        fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }
                                            .toSet()
                                }
                            }
                        }.onFailure { e ->
                            Log.e("MainActivity", "Authentication failed", e)
                        }
                    }
                }

                val removeBook: (Book) -> Unit = { book ->
                    val removeLocal: () -> Unit = {
                        books = books.filterNot { it.id.isNotEmpty() && it.id == book.id }.toSet()
                        scannedIsbns = scannedIsbns - book.isbn13
                        recentScannedBooks = recentScannedBooks.filterNot { recent ->
                            (book.id.isNotEmpty() && recent.id == book.id) ||
                                (book.id.isEmpty() && recent.isbn13 == book.isbn13)
                        }
                    }
                    if (book.id.isNotEmpty()) {
                        // Remove from backend
                        apiService.removeBook(book.id) { result ->
                            result.onSuccess {
                                removeLocal()
                            }.onFailure { e ->
                                Log.e("MainActivity", "Failed to remove book from backend", e)
                                // Still remove from local state for better UX
                                removeLocal()
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
                    if (isLoggedIn) {
                        apiService.getTags { tagsResult ->
                            tagsResult.fold(
                                onSuccess = { fetchedTags ->
                                    tags = fetchedTags
                                    apiService.getPreferences { prefsResult ->
                                        prefsResult.fold(
                                            onSuccess = { prefs ->
                                                val defaults = prefs.defaultTagIds.toSet()
                                                defaultTagIds = defaults
                                                selectedTagIds = defaults
                                                apiService.getUserBooks(defaults.toList()) { booksResult ->
                                                    booksResult.onSuccess { fetchedBooks ->
                                                        books = fetchedBooks.toSet()
                                                        scannedIsbns = fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }.toSet()
                                                    }.onFailure { e ->
                                                        Log.e("MainActivity", "Failed to fetch books", e)
                                                    }
                                                }
                                            },
                                            onFailure = { e ->
                                                Log.e("MainActivity", "Failed to fetch preferences", e)
                                                // Fallback to fetch all books
                                                apiService.getUserBooks(emptyList()) { booksResult ->
                                                    booksResult.onSuccess { fetchedBooks ->
                                                        books = fetchedBooks.toSet()
                                                        scannedIsbns = fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }.toSet()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                },
                                onFailure = { e ->
                                    Log.e("MainActivity", "Failed to fetch tags", e)
                                    // Fallback to fetch all books
                                    apiService.getUserBooks(emptyList()) { booksResult ->
                                        booksResult.onSuccess { fetchedBooks ->
                                            books = fetchedBooks.toSet()
                                            scannedIsbns = fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }.toSet()
                                        }
                                    }
                                }
                            )
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
                            coroutineScope.launch {
                                try {
                                    val result: GetCredentialResponse =
                                        credentialManager.getCredential(
                                            context = context,
                                            request = googleSignInRequest
                                        )
                                    val credential = result.credential
                                    when (credential) {
                                        is CustomCredential -> {
                                            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                try {
                                                    val googleIdTokenCredential =
                                                        GoogleIdTokenCredential.createFrom(
                                                            credential.data
                                                        )
                                                    val idToken =
                                                        googleIdTokenCredential.idToken
                                                    handleGoogleSignInToken(idToken)
                                                } catch (e: GoogleIdTokenParsingException) {
                                                    Log.e(
                                                        "MainActivity",
                                                        "Received an invalid google id token response",
                                                        e
                                                    )
                                                }
                                            } else {
                                                Log.e(
                                                    "MainActivity",
                                                    "Unexpected type of credential"
                                                )
                                            }
                                        }

                                        else -> {
                                            Log.e(
                                                "MainActivity",
                                                "Unexpected credential type ${credential::class.java}"
                                            )
                                        }
                                    }
                                } catch (e: GetCredentialException) {
                                    Log.e("MainActivity", "Sign in with Google failed", e)
                                }
                            }
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
                                    tags = tags,
                                    selectedTagIds = selectedTagIds,
                                    onTagSelectionChanged = { newTags ->
                                        selectedTagIds = newTags
                                        apiService.getUserBooks(newTags.toList()) { result ->
                                            result.onSuccess { fetchedBooks ->
                                                books = fetchedBooks.toSet()
                                                scannedIsbns = fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }.toSet()
                                            }
                                        }
                                    },
                                    onUpdateBookTags = { booksToUpdate, newTagNames ->
                                        var completed = 0
                                        if (booksToUpdate.isEmpty()) return@BooksScreen
                                        
                                        booksToUpdate.forEach { book ->
                                            apiService.setBookTags(book.id, newTagNames) { result ->
                                                completed++
                                                if (completed == booksToUpdate.size) {
                                                    // Refresh books
                                                    apiService.getUserBooks(selectedTagIds.toList()) { res ->
                                                        res.onSuccess { fetchedBooks ->
                                                            books = fetchedBooks.toSet()
                                                            scannedIsbns = fetchedBooks.mapNotNull { it.isbn13.takeIf { isbn -> isbn.isNotEmpty() } }.toSet()
                                                        }
                                                    }
                                                    // Refresh tags (in case new ones created)
                                                    apiService.getTags { res ->
                                                        res.onSuccess { fetchedTags ->
                                                            tags = fetchedTags
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onRemoveBook = removeBook,
                                    modifier = Modifier.padding(contentPadding)
                                        .background(MaterialTheme.colorScheme.background)
                                )
                            }
                            composable(Routes.SCANNER) {
                                ScannerScreen(
                                    books = recentScannedBooks,
                                    scannedIsbns = scannedIsbns,
                                    availableTags = tags,
                                    autoTagNames = scannerAutoTagNames,
                                    onRemoveBook = removeBook,
                                    onIsbnScanned = { isbn ->
                                        // Mark as scanned to prevent duplicates
                                        scannedIsbns = scannedIsbns + isbn
                                        // Backend will verify ISBN and add the book
                                        apiService.addBookByIsbn(isbn) { result ->
                                            result.onSuccess { backendBook ->
                                                // Book successfully verified and added to backend (upsert by id)
                                                books = books.filterNot { it.id.isNotEmpty() && it.id == backendBook.id }.toSet() + backendBook
                                                Log.d("MainActivity", "Book verified and added to backend: ${backendBook.title}")
                                                // Track recent scans for scanner list (most recent first, max 5)
                                                recentScannedBooks =
                                                    (listOf(backendBook) + recentScannedBooks.filterNot { b ->
                                                        (backendBook.id.isNotEmpty() && b.id == backendBook.id) ||
                                                            b.isbn13 == backendBook.isbn13
                                                    }).take(5)

                                                val autoNames = scannerAutoTagNames
                                                    .map { it.trim() }
                                                    .filter { it.isNotEmpty() }
                                                    .toSet()

                                                if (autoNames.isNotEmpty() && backendBook.id.isNotEmpty()) {
                                                    val mergedNames = (backendBook.tags.map { it.name } + autoNames)
                                                        .map { it.trim() }
                                                        .filter { it.isNotEmpty() }
                                                        .distinct()

                                                    apiService.setBookTags(backendBook.id, mergedNames) { tagsResult ->
                                                        tagsResult.onSuccess { updatedTags ->
                                                            val updatedBook = backendBook.copy(tags = updatedTags)
                                                            books = books.filterNot { it.id.isNotEmpty() && it.id == updatedBook.id }.toSet() + updatedBook
                                                            // Keep recent scan list in sync (don't reorder)
                                                            recentScannedBooks = recentScannedBooks.map { b ->
                                                                if ((updatedBook.id.isNotEmpty() && b.id == updatedBook.id) || b.isbn13 == updatedBook.isbn13) updatedBook else b
                                                            }
                                                            // Refresh tag list in case new tags were created from the scanner dialog
                                                            apiService.getTags { res ->
                                                                res.onSuccess { fetchedTags -> tags = fetchedTags }
                                                            }
                                                        }.onFailure { e ->
                                                            Log.e("MainActivity", "Failed to auto-tag book ${backendBook.id}", e)
                                                        }
                                                    }
                                                }
                                            }.onFailure { e ->
                                                Log.e("MainActivity", "Failed to verify/add book to backend: $isbn", e)
                                                // Still mark as scanned to prevent retry spam
                                            }
                                        }
                                    },
                                    onAutoTagNamesChanged = { newNames -> scannerAutoTagNames = newNames },
                                    onLeaveScreen = { recentScannedBooks = emptyList() },
                                    modifier = Modifier.padding(contentPadding),
                                )
                            }
                            composable(Routes.PROFILE) {
                                ProfileScreen(
                                    tags = tags,
                                    defaultTagIds = defaultTagIds,
                                    onUpdateDefaultTags = { names ->
                                        val newIds = mutableListOf<String>()
                                        val namesToProcess = names.toMutableList()
                                        
                                        fun processNext() {
                                            if (namesToProcess.isEmpty()) {
                                                apiService.updatePreferences(newIds) { result ->
                                                    result.onSuccess { prefs ->
                                                        defaultTagIds = prefs.defaultTagIds.toSet()
                                                        apiService.getTags { res -> 
                                                            res.onSuccess { tags = it }
                                                        }
                                                    }
                                                }
                                                return
                                            }
                                            val name = namesToProcess.removeAt(0)
                                            val existing = tags.find { it.name.equals(name, ignoreCase = true) }
                                            if (existing != null) {
                                                newIds.add(existing.id)
                                                processNext()
                                            } else {
                                                apiService.createTag(name) { result ->
                                                    result.onSuccess { tag ->
                                                        newIds.add(tag.id)
                                                        processNext()
                                                    }.onFailure {
                                                        processNext()
                                                    }
                                                }
                                            }
                                        }
                                        processNext()
                                    },
                                    onSignOut = {
                                        // Clear token and sign out
                                        tokenStorage.clearToken()
                                        coroutineScope.launch {
                                            try {
                                                credentialManager.clearCredentialState(
                                                    ClearCredentialStateRequest()
                                                )
                                            } catch (e: ClearCredentialException) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Failed to clear credential state",
                                                    e
                                                )
                                            }
                                        }
                                        isLoggedIn = false
                                        books = emptySet()
                                        scannedIsbns = emptySet()
                                        recentScannedBooks = emptyList()
                                        tags = emptyList()
                                        selectedTagIds = emptySet()
                                        defaultTagIds = emptySet()
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