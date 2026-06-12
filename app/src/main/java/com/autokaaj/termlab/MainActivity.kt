package com.autokaaj.termlab

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var hiddenInput: EditText
    private var outputStream: FileOutputStream? = null
    private var isPtyStarted = false
    private var isCtrlPressed = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview_terminal)
        hiddenInput = findViewById(R.id.hidden_input)

        // টার্মিনাল সেটআপ
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isPtyStarted) {
                    isPtyStarted = true
                    startPTY()
                }
            }
        }
        webView.loadUrl("file:///android_asset/xterm/index.html")

        // স্ক্রিনে টাচ করলে নেটিভ কীবোর্ড চালু হবে
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                hiddenInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
            }
            false
        }

        // ম্যাজিক: কীবোর্ড ইনপুট সরাসরি C++ ইঞ্জিনে পাঠানো
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.isNotEmpty()) {
                    var charToSend = s.toString()
                    
                    // CTRL প্রসেসিং
                    if (isCtrlPressed) {
                        val c = charToSend[0].lowercaseChar()
                        if (c in 'a'..'z') {
                            charToSend = (c - 'a' + 1).toChar().toString()
                        }
                        isCtrlPressed = false
                        findViewById<Button>(R.id.btn_ctrl).setBackgroundColor(Color.parseColor("#333333"))
                    }
                    
                    writeToPty(charToSend)
                    
                    // ইনপুট ক্লিয়ার করা
                    hiddenInput.removeTextChangedListener(this)
                    hiddenInput.setText("")
                    hiddenInput.addTextChangedListener(this)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // হার্ডওয়্যার/সফট এন্টার এবং ব্যাকস্পেস হ্যান্ডেল করা
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    writeToPty("\u007F") // ব্যাকস্পেস
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    writeToPty("\r") // এন্টার
                    return@setOnKeyListener true
                }
            }
            false
        }

        // নেটিভ এক্সট্রা কীবোর্ড বাটন সেটআপ
        findViewById<Button>(R.id.btn_ctrl).setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            val color = if (isCtrlPressed) "#555555" else "#333333"
            it.setBackgroundColor(Color.parseColor(color))
        }
        findViewById<Button>(R.id.btn_esc).setOnClickListener { writeToPty("\u001B") }
        findViewById<Button>(R.id.btn_tab).setOnClickListener { writeToPty("\t") }
        findViewById<Button>(R.id.btn_dash).setOnClickListener { writeToPty("-") }
        findViewById<Button>(R.id.btn_slash).setOnClickListener { writeToPty("/") }
        findViewById<Button>(R.id.btn_enter).setOnClickListener { writeToPty("\r") }
    }

    private fun writeToPty(str: String) {
        try {
            outputStream?.write(str.toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
