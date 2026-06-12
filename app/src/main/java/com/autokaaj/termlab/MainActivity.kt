package com.autokaaj.termlab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // PTY Core যুক্ত হওয়ার পর এখানে Terminal ইঞ্জিন কল করা হবে
    }
}
