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

        setupExtraKeys()

        // টার্মিনালে টাচ করলে ওয়েবভিউ ফোকাস পাবে না, ফোকাস পাবে আমাদের লুকানো EditText
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                hiddenInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
            }
            true // True দেওয়ার মানে হলো ওয়েবভিউ এই টাচ ইভেন্টটি ব্যবহার করতে পারবে না
        }

        // কীবোর্ডে টাইপ করা অক্ষর সরাসরি পড়া
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    val input = s.toString()
                    
                    // ইনপুটটি C++ এ পাঠানোর পর সাথে সাথে মুছে ফেলা
                    hiddenInput.removeTextChangedListener(this)
                    hiddenInput.setText("")
                    hiddenInput.addTextChangedListener(this)

                    // যদি এন্টার চাপ দেওয়া হয় (Line break)
                    if (input.contains("\n")) {
                        writeToPty("\r")
                        return
                    }

                    // CTRL হ্যান্ডেল করা
                    var charToSend = input
                    if (isCtrlPressed && input.length == 1) {
                        val c = input[0].lowercaseChar()
                        if (c in 'a'..'z') {
                            charToSend = (c - 'a' + 1).toChar().toString()
                        }
                        isCtrlPressed = false
                        findViewById<Button>(R.id.btn_ctrl).setBackgroundColor(Color.parseColor("#333333"))
                    }
                    writeToPty(charToSend)
                }
            }
        })

        // হার্ডওয়্যার ব্যাকস্পেস এবং এন্টার ফিক্স
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> { writeToPty("\u007F"); return@setOnKeyListener true }
                    KeyEvent.KEYCODE_ENTER -> { writeToPty("\r"); return@setOnKeyListener true }
                }
            }
            false
        }
    }

    private fun setupExtraKeys() {
        val btnCtrl = findViewById<Button>(R.id.btn_ctrl)
        btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            btnCtrl.setBackgroundColor(Color.parseColor(if (isCtrlPressed) "#555555" else "#333333"))
        }

        setKeyBtn(R.id.btn_esc, "\u001B")
        setKeyBtn(R.id.btn_tab, "\t")
        setKeyBtn(R.id.btn_dash, "-")
        setKeyBtn(R.id.btn_slash, "/")
        
        // অ্যারো বাটন
        setKeyBtn(R.id.btn_up, "\u001B[A")
        setKeyBtn(R.id.btn_down, "\u001B[B")
        setKeyBtn(R.id.btn_right, "\u001B[C")
        setKeyBtn(R.id.btn_left, "\u001B[D")
    }

    private fun setKeyBtn(id: Int, sequence: String) {
        findViewById<Button>(id).setOnClickListener {
            writeToPty(sequence)
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
