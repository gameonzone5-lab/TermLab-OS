package com.autokaaj.termlab

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var outputStream: FileOutputStream? = null
    private var isPtyStarted = false
    private var isCtrlPressed = false
    private var isAltPressed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview_terminal)

        // WebView কনফিগারেশন
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        setupExtraKeys()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isPtyStarted) {
                    isPtyStarted = true
                    startPTY()
                }
            }
        }
        webView.loadUrl("file:///android_asset/xterm/index.html")
    }

    private fun setupExtraKeys() {
        val btnCtrl = findViewById<Button>(R.id.btn_ctrl)
        btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            btnCtrl.setBackgroundColor(Color.parseColor(if (isCtrlPressed) "#555555" else "#333333"))
        }

        val btnAlt = findViewById<Button>(R.id.btn_alt)
        btnAlt.setOnClickListener {
            isAltPressed = !isAltPressed
            btnAlt.setBackgroundColor(Color.parseColor(if (isAltPressed) "#555555" else "#333333"))
        }

        // সাধারণ বাটন
        setKeyBtn(R.id.btn_esc, "\u001B")
        setKeyBtn(R.id.btn_tab, "\t")
        setKeyBtn(R.id.btn_dash, "-")
        setKeyBtn(R.id.btn_slash, "/")

        // অ্যারো বাটন (Linux ANSI Escape sequences)
        setKeyBtn(R.id.btn_up, "\u001B[A")
        setKeyBtn(R.id.btn_down, "\u001B[B")
        setKeyBtn(R.id.btn_right, "\u001B[C")
        setKeyBtn(R.id.btn_left, "\u001B[D")
    }

    private fun setKeyBtn(id: Int, sequence: String) {
        findViewById<Button>(id).setOnClickListener {
            writeToPty(sequence)
            webView.evaluateJavascript("window.focusTerm();", null)
        }
    }

    // ম্যাজিক: xterm.js থেকে সরাসরি টাইপিং রিসিভ করা
    inner class WebAppInterface {
        @JavascriptInterface
        fun sendInput(input: String) {
            var charToSend = input

            // CTRL লজিক (যেমন CTRL+C)
            if (isCtrlPressed && input.length == 1) {
                val c = input[0].lowercaseChar()
                if (c in 'a'..'z') {
                    charToSend = (c - 'a' + 1).toChar().toString()
                }
                isCtrlPressed = false
                runOnUiThread { findViewById<Button>(R.id.btn_ctrl).setBackgroundColor(Color.parseColor("#333333")) }
            }

            // ALT লজিক
            if (isAltPressed) {
                charToSend = "\u001B$charToSend"
                isAltPressed = false
                runOnUiThread { findViewById<Button>(R.id.btn_alt).setBackgroundColor(Color.parseColor("#333333")) }
            }

            writeToPty(charToSend)
        }
    }

    private fun writeToPty(str: String) {
        try {
            outputStream?.write(str.toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {}
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
                } catch (e: Exception) {}
            }
        }
    }

    external fun createPTY(): Int
    companion object { init { System.loadLibrary("termlab_native") } }
}
