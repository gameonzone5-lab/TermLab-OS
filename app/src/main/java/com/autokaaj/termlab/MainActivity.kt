package com.autokaaj.termlab

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var tempTerminalText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tempTerminalText = findViewById(R.id.temp_terminal_text)
        tempTerminalText.text = "[System] Booting PTY Engine...\n"

        // C++ থেকে PTY তৈরি করা
        val ptyFd = createPTY()
        
        if (ptyFd > 0) {
            val pfd = ParcelFileDescriptor.adoptFd(ptyFd)
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)

            // ব্যাকগ্রাউন্ড থ্রেড: শেল থেকে সবসময় আউটপুট পড়ে স্ক্রিনে দেখাবে
            thread {
                val buffer = ByteArray(4096)
                var read: Int
                try {
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        val output = String(buffer, 0, read)
                        runOnUiThread {
                            tempTerminalText.append(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // টেস্ট করার জন্য ১ সেকেন্ড পর ব্যাকগ্রাউন্ডে একটি লিনাক্স কমান্ড পাঠানো
            thread {
                Thread.sleep(1000)
                outputStream.write("echo '\n--- System Information ---'\nid\nls -l /\n".toByteArray())
            }

        } else {
            tempTerminalText.append("[Error] Failed to initialize PTY core!\n")
        }
    }

    // C++ ফাংশন কল
    external fun createPTY(): Int

    companion object {
        init {
            System.loadLibrary("termlab_native")
        }
    }
}
