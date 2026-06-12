package com.autokaaj.termlab

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tempTerminalText = findViewById<TextView>(R.id.temp_terminal_text)
        
        // C++ থেকে ডেটা নিয়ে স্ক্রিনে সেট করা
        tempTerminalText.text = stringFromJNI()
    }

    external fun stringFromJNI(): String

    companion object {
        init {
            // C++ লাইব্রেরিটি লোড করা
            System.loadLibrary("termlab_native")
        }
    }
}
