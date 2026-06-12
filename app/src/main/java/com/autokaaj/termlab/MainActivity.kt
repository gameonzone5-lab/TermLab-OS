package com.autokaaj.termlab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.termux.view.TerminalView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val terminalView = findViewById<TerminalView>(R.id.terminal_view)
        // PTY এবং Shell সেশনের কোড আমরা পরে এখানে যুক্ত করব
    }
}
