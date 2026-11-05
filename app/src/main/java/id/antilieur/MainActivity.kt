package id.antilieur

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val REQ_NOTIF = 10
    }

    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private var startAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggle)
        statusText = findViewById(R.id.status)

        toggleButton.setOnClickListener {
            if (MotionCueService.isRunning) {
                MotionCueService.stop(this)
                toggleButton.postDelayed({ updateUi() }, 250)
            } else {
                tryStart()
            }
        }

        val s = CueSettings.load(this)
        bindSlider(R.id.seek_sensitivity, R.id.label_sensitivity, CueSettings.KEY_SENSITIVITY,
            s.sensitivity, R.string.label_sensitivity) { "$it" }
        bindSlider(R.id.seek_size, R.id.label_size, CueSettings.KEY_DOT_SIZE,
            s.dotSizeDp, R.string.label_size) { "$it dp" }
        bindSlider(R.id.seek_rows, R.id.label_rows, CueSettings.KEY_ROWS,
            s.rows, R.string.label_rows) { "$it" }
        bindSlider(R.id.seek_opacity, R.id.label_opacity, CueSettings.KEY_OPACITY,
            s.opacity, R.string.label_opacity) { "$it%" }

        findViewById<Button>(R.id.reset).setOnClickListener {
            CueSettings.prefs(this).edit().clear().apply()
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari halaman izin overlay: langsung mulai jika izin sudah diberikan
        if (startAfterPermission && Settings.canDrawOverlays(this)) {
            startAfterPermission = false
            tryStart()
        }
        updateUi()
    }

    private fun bindSlider(
        seekId: Int, labelId: Int, key: String, value: Int, titleRes: Int,
        format: (Int) -> String,
    ) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(labelId)
        val title = getString(titleRes)
        seek.progress = value
        label.text = "$title: ${format(value)}"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                label.text = "$title: ${format(progress)}"
                if (fromUser) CueSettings.prefs(this@MainActivity).edit().putInt(key, progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun tryStart() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.overlay_title)
                .setMessage(R.string.overlay_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startAfterPermission = true
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !CueSettings.prefs(this).getBoolean("asked_notif", false)
        ) {
            CueSettings.prefs(this).edit().putBoolean("asked_notif", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }

        MotionCueService.start(this)
        toggleButton.postDelayed({ updateUi() }, 300)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Notifikasi opsional; titik tetap berjalan meskipun izin ditolak
        if (requestCode == REQ_NOTIF) tryStart()
    }

    private fun updateUi() {
        val on = MotionCueService.isRunning
        toggleButton.setText(if (on) R.string.stop else R.string.start)
        toggleButton.setBackgroundResource(if (on) R.drawable.btn_stop else R.drawable.btn_start)
        statusText.setText(if (on) R.string.status_on else R.string.status_off)
    }
}
