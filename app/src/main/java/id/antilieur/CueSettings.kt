package id.antilieur

import android.content.Context
import android.content.SharedPreferences

/** Pengaturan yang bisa diubah pengguna, disimpan di SharedPreferences. */
data class CueSettings(
    val sensitivity: Int,   // 1..10
    val dotSizeDp: Int,     // 3..14
    val rows: Int,          // 3..10 titik per kolom
    val opacity: Int,       // 20..100 (%)
) {
    companion object {
        const val PREFS = "anti_lieur"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_DOT_SIZE = "dot_size"
        const val KEY_ROWS = "rows"
        const val KEY_OPACITY = "opacity"

        val DEFAULT = CueSettings(sensitivity = 5, dotSizeDp = 7, rows = 6, opacity = 70)

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(ctx: Context): CueSettings {
            val p = prefs(ctx)
            return CueSettings(
                sensitivity = p.getInt(KEY_SENSITIVITY, DEFAULT.sensitivity),
                dotSizeDp = p.getInt(KEY_DOT_SIZE, DEFAULT.dotSizeDp),
                rows = p.getInt(KEY_ROWS, DEFAULT.rows),
                opacity = p.getInt(KEY_OPACITY, DEFAULT.opacity),
            )
        }
    }
}
