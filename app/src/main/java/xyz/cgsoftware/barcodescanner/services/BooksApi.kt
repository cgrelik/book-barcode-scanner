package xyz.cgsoftware.barcodescanner.services

import android.util.Log
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.json.JSONObject
import xyz.cgsoftware.barcodescanner.models.Book
import java.nio.ByteBuffer

private const val TAG = "BooksApiRequestCallback"

class BooksApiRequestCallback(val onSuccess: (book: Book) -> Unit): UrlRequest.Callback() {
    private val responseBody = StringBuilder()
    override fun onRedirectReceived(request: UrlRequest?, info: UrlResponseInfo?, newLocationUrl: String?) {
        Log.i(TAG, "onRedirectReceived method called.")
        // You should call the request.followRedirect() method to continue
        // processing the request.
        request?.followRedirect()
    }

    override fun onResponseStarted(
        request: UrlRequest?,
        info: UrlResponseInfo?
    ) {
        Log.i(TAG, "onResponseStarted method called.")
        // You should call the request.read() method before the request can be
        // further processed. The following instruction provides a ByteBuffer object
        // with a capacity of 102400 bytes for the read() method. The same buffer
        // with data is passed to the onReadCompleted() method.
        request?.read(ByteBuffer.allocateDirect(102400))
    }

    override fun onReadCompleted(
        request: UrlRequest?,
        info: UrlResponseInfo?,
        byteBuffer: ByteBuffer?
    ) {
        Log.i(TAG, "onReadCompleted method called.")
        byteBuffer?.let {
            Log.i(TAG, "Reading more")
            it.flip()
            val bytes = ByteArray(it.remaining())
            it.get(bytes)
            responseBody.append(String(bytes, Charsets.UTF_8))
            byteBuffer.clear()
            request?.read(it)
        }

    }

    override fun onSucceeded(
        request: UrlRequest?,
        info: UrlResponseInfo?
    ) {
        Log.i(TAG, "onSucceeded method called.")
        if (responseBody.isNotEmpty()) {
            val jsonObject = JSONObject(responseBody.toString())
            val totalItems = jsonObject.getInt("totalItems")
            if (totalItems == 1) {
                val book = jsonObject.getJSONArray("items")
                    .getJSONObject(0)
                    .getJSONObject("volumeInfo")
                val title = book.getString("title")
                val identifiersJson = book.getJSONArray("industryIdentifiers")
                var isbn13 = ""
                var isbn10 = ""
                for (i in 0 until identifiersJson.length()) {
                    if (identifiersJson.getJSONObject(i).getString("type") == "ISBN_13") {
                        isbn13 = identifiersJson.getJSONObject(i).getString("identifier")
                    }
                    if (identifiersJson.getJSONObject(i).getString("type") == "ISBN_10") {
                        isbn10 = identifiersJson.getJSONObject(i).getString("identifier")
                    }
                }
                val thumbnail = if (book.has("imageLinks")) {
                    book.getJSONObject("imageLinks").getString("smallThumbnail")
                        .replace("http:", "https:")
                } else {
                    null
                }

                onSuccess(Book(isbn13, isbn10, title, thumbnail))
            }
        }
    }

    override fun onFailed(
        p0: UrlRequest?,
        p1: UrlResponseInfo?,
        p2: CronetException?
    ) {
        Log.i(TAG, "onFailed method called.")
    }
}