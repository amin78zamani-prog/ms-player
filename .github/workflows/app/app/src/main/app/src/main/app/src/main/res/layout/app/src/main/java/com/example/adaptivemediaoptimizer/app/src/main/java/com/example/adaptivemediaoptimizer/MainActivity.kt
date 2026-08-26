package com.example.adaptivemediaoptimizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var profiler: SystemProfiler
    
    private val logRecords = mutableListOf<SystemMetrics>()
    private val handler = Handler(Looper.getMainLooper())
    private var isSmartMode = false

    private val defaultStreamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    private val monitorRunnable = object : Runnable {
        override fun run() {
            val metrics = profiler.captureMetrics(isSmartMode)
            logRecords.add(metrics)
            updateUI(metrics)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profiler = SystemProfiler(this)
        playerView = findViewById(R.id.playerView)

        val etVideoUrl = findViewById<EditText>(R.id.etVideoUrl)
        val btnPlayUrl = findViewById<Button>(R.id.btnPlayUrl)
        val switchSmartMode = findViewById<SwitchMaterial>(R.id.switchSmartMode)
        val btnExportLog = findViewById<Button>(R.id.btnExportLog)

        initPlayer()

        handleIncomingIntent(intent)

        btnPlayUrl.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                playMedia(url)
            } else {
                Toast.makeText(this, "لطفاً لینک ویدیو را وارد کنید", Toast.LENGTH_SHORT).show()
            }
        }

        switchSmartMode.setOnCheckedChangeListener { _, isChecked ->
            isSmartMode = isChecked
            applyEnergyPolicy(isChecked)
        }

        btnExportLog.setOnClickListener {
            saveLogsToCsv()
        }

        handler.post(monitorRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val videoUri = intent.data
            videoUri?.let {
                findViewById<EditText>(R.id.etVideoUrl).setText(it.toString())
                playMedia(it.toString())
            }
        } else {
            playMedia(defaultStreamUrl)
        }
    }

    private fun initPlayer() {
        trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        playerView.player = player
    }

    private fun playMedia(url: String) {
        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در بارگذاری ویدیو: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyEnergyPolicy(enableSmart: Boolean) {
        if (enableSmart) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(854, 480)
                    .setMaxVideoBitrate(800_000)
            )
            Toast.makeText(this, "سیاست بهینه (سقف 480p) فعال شد", Toast.LENGTH_SHORT).show()
        } else {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
            )
            Toast.makeText(this, "حالت عادی (بدون محدودیت) فعال شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(m: SystemMetrics) {
        findViewById<TextView>(R.id.txtBattery).text = 
            "باتری: ${String.format("%.1f", m.batteryLevel)}% | ولتاژ: ${m.voltageMv} mV"
        findViewById<TextView>(R.id.txtNetwork).text = 
            "سیگنال وای‌فای: ${m.wifiRssi} dBm"
        findViewById<TextView>(R.id.txtTraffic).text = 
            "حجم ترافیک دریافتی: ${m.rxBytesTotal / 1024} KB"
    }

    private fun saveLogsToCsv() {
        try {
            val file = File(getExternalFilesDir(null), "MS_Player_Energy_Log.csv")
            val writer = FileWriter(file)
            writer.append("Timestamp,BatteryLevel,Voltage_mV,WifiRSSI,RxBytes,IsSmartMode\n")

            for (record in logRecords) {
                writer.append("${record.timestamp},${record.batteryLevel},${record.voltageMv},${record.wifiRssi},${record.rxBytesTotal},${record.isSmartModeActive}\n")
            }
            writer.flush()
            writer.close()

            findViewById<TextView>(R.id.txtLogStatus).text = "لاگ ذخیره شد:\n${file.absolutePath}"
            Toast.makeText(this, "فایل لاگ ذخیره شد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره لاگ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
        player?.release()
        player = null
    }
}
