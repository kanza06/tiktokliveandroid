package com.ombaharbangkit.tiktoklivereader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Layar loading — sama seperti SplashWindow di main.py:
 * logo + "Memuat aplikasi... N%" + progress bar, lalu masuk ke dashboard.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pct = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val bar = findViewById<ProgressBar>(R.id.splashBar)
        val status = findViewById<TextView>(R.id.splashStatus)

        val tick = object : Runnable {
            override fun run() {
                pct = minOf(pct + 5, 100)
                bar.progress = pct
                status.text = "Memuat aplikasi... $pct%"
                if (pct >= 100) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                } else {
                    handler.postDelayed(this, 45)
                }
            }
        }
        handler.postDelayed(tick, 45)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
