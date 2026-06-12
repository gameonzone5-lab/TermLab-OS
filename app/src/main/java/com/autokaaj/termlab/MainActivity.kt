package com.autokaaj.termlab

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var outputStream: FileOutputStream? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview_terminal)
        
        // WebView কনফিগারেশন
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        
        // জাভাস্ক্রিপ্ট থেকে অ্যান্ড্রয়েডে কল রিসিভ করার ব্রিজ
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // আমাদের তৈরি করা টার্মিনাল UI লোড করা
        webView.loadUrl("file:///android_asset/xterm/index.html")

        // C++ কোর থেকে শেল চালু করা
        startPTY()
    }

    private fun startPTY() {
        val ptyFd = createPTY()
        if (ptyFd > 0) {
            val pfd = ParcelFileDescriptor.adoptFd(ptyFd)
            val inputStream = FileInputStream(pfd.fileDescriptor)
            outputStream = FileOutputStream(pfd.fileDescriptor)

            // ব্যাকগ্রাউন্ড থ্রেড: শেল থেকে আউটপুট পড়বে এবং WebView-তে পাঠাবে
            thread {
                val buffer = ByteArray(4096)
                var read: Int
                try {
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        val b64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        runOnUiThread {
                            webView.evaluateJavascript("window.writeBase64('$b64');", null)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // JS থেকে ইনপুট নিয়ে C++ শেলে পাঠানো
    inner class WebAppInterface {
        @JavascriptInterface
        fun sendInput(input: String) {
            try {
                outputStream?.write(input.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    external fun createPTY(): Int

    companion object {
        init {
            System.loadLibrary("termlab_native")
        }
    }
}
