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
    private var isPtyStarted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview_terminal)
        
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // গ্রাফিক্স (WebView) পুরোপুরি লোড হওয়ার পরই কেবল C++ শেল চালু হবে
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isPtyStarted) {
                    isPtyStarted = true
                    startPTY()
                }
            }
        }

        webView.loadUrl("file:///android_asset/xterm/index.html")
    }

    private fun startPTY() {
        val ptyFd = createPTY()
        if (ptyFd > 0) {
            val pfd = ParcelFileDescriptor.adoptFd(ptyFd)
            val inputStream = FileInputStream(pfd.fileDescriptor)
            outputStream = FileOutputStream(pfd.fileDescriptor)

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
