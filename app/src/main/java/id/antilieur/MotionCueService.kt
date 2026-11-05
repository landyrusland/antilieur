package id.antilieur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import kotlin.math.sqrt

/**
 * Layanan latar depan yang membaca sensor gerak lalu menggerakkan titik isyarat
 * pada lapisan overlay di atas semua aplikasi.
 */
class MotionCueService : Service(), SensorEventListener {

    companion object {
        const val ACTION_STOP = "id.antilieur.action.STOP"
        private const val CHANNEL_ID = "motion_cues"
        private const val NOTIF_ID = 1

        @Volatile
        var isRunning = false
            private set

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, MotionCueService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MotionCueService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var sensorManager: SensorManager
    private var overlay: MotionCueView? = null

    private val gravityVec = FloatArray(3)
    private val linear = FloatArray(3)
    private var hasGravity = false
    private var displayRotation = Surface.ROTATION_0

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        updateRotation()
        addOverlay()
        registerSensors()
        CueSettings.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateRotation()
    }

    override fun onDestroy() {
        isRunning = false
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        CueSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Notifikasi

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MotionCueService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dots)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.stop), stopIntent
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ---------------------------------------------------------------- Overlay

    private fun addOverlay() {
        val view = MotionCueView(this)
        view.applySettings(CueSettings.load(this))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Android 12+ hanya meneruskan sentuhan ke aplikasi di bawah overlay
            // jika opasitas jendela <= 0.8. Jangan dinaikkan.
            alpha = 0.8f
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowManager.addView(view, params)
        overlay = view
    }

    private fun applySettings() {
        overlay?.applySettings(CueSettings.load(this))
    }

    @Suppress("DEPRECATION")
    private fun updateRotation() {
        if (::windowManager.isInitialized) {
            displayRotation = windowManager.defaultDisplay.rotation
        }
    }

    // ---------------------------------------------------------------- Sensor

    private fun registerSensors() {
        val lin = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val grav = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (lin != null && grav != null) {
            sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, lin, SensorManager.SENSOR_DELAY_GAME)
        } else {
            // HP tanpa sensor virtual: hitung sendiri dari akselerometer biasa
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravityVec[0] = v[0]; gravityVec[1] = v[1]; gravityVec[2] = v[2]
                hasGravity = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                linear[0] = v[0]; linear[1] = v[1]; linear[2] = v[2]
                process()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasGravity) {
                    gravityVec[0] = v[0]; gravityVec[1] = v[1]; gravityVec[2] = v[2]
                    hasGravity = true
                }
                for (i in 0..2) {
                    gravityVec[i] = 0.92f * gravityVec[i] + 0.08f * v[i]   // filter low-pass
                    linear[i] = v[i] - gravityVec[i]
                }
                process()
            }
        }
    }

    /**
     * Mengubah percepatan dari sumbu HP menjadi percepatan kendaraan pada bidang datar:
     * - lateral: ke arah kanan layar
     * - forward: ke arah depan (menjauhi pengguna)
     * Bekerja untuk HP yang dipegang tegak, miring, maupun mendatar, dan untuk mode landscape.
     */
    private fun process() {
        if (!hasGravity) return
        val gn = sqrt(gravityVec[0] * gravityVec[0] + gravityVec[1] * gravityVec[1] + gravityVec[2] * gravityVec[2])
        if (gn < 1f) return
        val ux = gravityVec[0] / gn
        val uy = gravityVec[1] / gn
        val uz = gravityVec[2] / gn

        // Arah "kanan layar" dan "atas layar" dalam koordinat sensor, sesuai rotasi layar
        val rx: Float; val ry: Float; val upx: Float; val upy: Float
        when (displayRotation) {
            Surface.ROTATION_90 -> { rx = 0f; ry = -1f; upx = 1f; upy = 0f }
            Surface.ROTATION_180 -> { rx = -1f; ry = 0f; upx = 0f; upy = -1f }
            Surface.ROTATION_270 -> { rx = 0f; ry = 1f; upx = -1f; upy = 0f }
            else -> { rx = 1f; ry = 0f; upx = 0f; upy = 1f }
        }

        // Proyeksikan ke bidang horizontal (buang komponen searah gravitasi)
        val rDot = rx * ux + ry * uy
        var lx = rx - rDot * ux
        var ly = ry - rDot * uy
        var lz = 0f - rDot * uz

        // Depan = atas layar + arah belakang HP (-Z). Mencakup HP tegak maupun mendatar.
        val fx0 = upx; val fy0 = upy; val fz0 = -1f
        val fDot = fx0 * ux + fy0 * uy + fz0 * uz
        var fx = fx0 - fDot * ux
        var fy = fy0 - fDot * uy
        var fz = fz0 - fDot * uz

        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        val fn = sqrt(fx * fx + fy * fy + fz * fz)
        if (ln < 0.1f || fn < 0.1f) return
        lx /= ln; ly /= ln; lz /= ln
        fx /= fn; fy /= fn; fz /= fn

        val lateral = linear[0] * lx + linear[1] * ly + linear[2] * lz
        val forward = linear[0] * fx + linear[1] * fy + linear[2] * fz
        overlay?.setAcceleration(lateral, forward)
    }
}
