package id.antilieur

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Lapisan transparan berisi titik-titik di tepi kiri dan kanan layar.
 *
 * Titik bergeser berlawanan dengan percepatan kendaraan (seperti pemandangan di luar
 * jendela), sehingga mata "melihat" gerakan yang dirasakan telinga bagian dalam.
 * Konflik antara penglihatan dan rasa keseimbangan inilah penyebab utama mabuk perjalanan.
 */
class MotionCueView(context: Context) : View(context), Choreographer.FrameCallback {

    private val density = resources.displayMetrics.density

    // Pengaturan
    private var pxPerMs2 = 30f * density      // geseran (px) per 1 m/s² percepatan
    private var maxOffset = 80f * density
    private var dotRadius = 3.5f * density
    private var rows = 6

    // Percepatan yang sudah dihaluskan (m/s²)
    private var smoothLateral = 0f
    private var smoothForward = 0f

    // Model pegas-peredam agar gerakan titik halus dan alami
    private var targetX = 0f
    private var targetY = 0f
    private var posX = 0f
    private var posY = 0f
    private var velX = 0f
    private var velY = 0f
    private var lastFrameNanos = 0L
    private var running = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        applySettings(CueSettings.DEFAULT)
    }

    fun applySettings(s: CueSettings) {
        pxPerMs2 = s.sensitivity * 6f * density
        maxOffset = (40f + s.sensitivity * 8f) * density
        dotRadius = s.dotSizeDp * density / 2f
        rows = s.rows.coerceIn(3, 10)
        val a = (s.opacity.coerceIn(20, 100) * 2.55f).toInt()
        // Isi gelap + cincin terang: tetap terlihat di latar terang maupun gelap
        fillPaint.color = Color.argb(a, 25, 25, 25)
        ringPaint.color = Color.argb(a, 255, 255, 255)
        ringPaint.strokeWidth = dotRadius * 0.35f
        invalidate()
    }

    /**
     * @param lateral percepatan ke kanan layar (m/s²); positif saat belok kanan.
     * @param forward percepatan ke arah depan kendaraan (m/s²); positif saat menambah kecepatan.
     */
    fun setAcceleration(lateral: Float, forward: Float) {
        smoothLateral += 0.25f * (deadZone(lateral) - smoothLateral)
        smoothForward += 0.25f * (deadZone(forward) - smoothForward)
        // Belok kanan -> pemandangan bergeser ke kiri. Mobil maju -> pemandangan "mendekat" (turun).
        targetX = (-smoothLateral * pxPerMs2).coerceIn(-maxOffset, maxOffset)
        targetY = (smoothForward * pxPerMs2).coerceIn(-maxOffset, maxOffset)
    }

    private fun deadZone(v: Float): Float {
        val dz = 0.12f // abaikan getaran kecil tangan / jalan
        return if (abs(v) < dz) 0f else v - dz * sign(v)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 0.016f
        else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = frameTimeNanos

        val stiffness = 55f
        val damping = 2f * sqrt(stiffness) * 0.85f
        velX += (stiffness * (targetX - posX) - damping * velX) * dt
        velY += (stiffness * (targetY - posY) - damping * velY) * dt
        val oldX = posX
        val oldY = posY
        posX += velX * dt
        posY += velY * dt

        if (abs(posX - oldX) > 0.05f || abs(posY - oldY) > 0.05f) invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val edge = dotRadius * 3f
        val gap = dotRadius * 4f
        val columns = floatArrayOf(edge, edge + gap, w - edge - gap, w - edge)
        val spacing = h / (rows + 1)

        for (r in 0 until rows) {
            // Kolom bagian dalam digeser setengah baris agar pola terlihat seperti kisi
            for ((ci, cx) in columns.withIndex()) {
                val inner = ci == 1 || ci == 2
                val cy = spacing * (r + 1) + (if (inner) spacing / 2f else 0f)
                if (inner && r == rows - 1) continue
                val x = cx + posX
                val y = cy + posY
                canvas.drawCircle(x, y, dotRadius, fillPaint)
                canvas.drawCircle(x, y, dotRadius, ringPaint)
            }
        }
    }
}
