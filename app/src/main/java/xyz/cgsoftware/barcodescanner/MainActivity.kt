package xyz.cgsoftware.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastFilterNotNull
import androidx.core.content.ContextCompat
import xyz.cgsoftware.barcodescanner.models.Book
import xyz.cgsoftware.barcodescanner.ui.theme.BarcodeScannerTheme

enum class Screen {
    Books,
    Scanner
}

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
                MainApp()
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

@Composable
fun MainApp(modifier: Modifier = Modifier) {
    var currentScreen by remember { mutableStateOf(Screen.Books) }
    var books by remember { mutableStateOf(setOf<Book>()) }
    var isbns by remember { mutableStateOf(setOf<String>()) }

    val baseModifier = modifier
        .fillMaxSize()
        .navigationBarsPadding()
        .statusBarsPadding()
        .systemBarsPadding()

    when (currentScreen) {
        Screen.Books -> {
            BooksScreen(
                books = books,
                onScanClick = { currentScreen = Screen.Scanner },
                onDeleteBook = { book -> books = books - book },
                modifier = baseModifier
            )
        }
        Screen.Scanner -> {
            ScannerScreen(
                scannedBooks = books,
                scannedIsbns = isbns,
                onBookScanned = { book, isbn ->
                    books = books + book
                    isbns = isbns + isbn
                },
                onDeleteBook = { book -> books = books - book },
                onBackClick = { currentScreen = Screen.Books },
                modifier = baseModifier
            )
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
