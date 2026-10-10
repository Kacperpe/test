package pl.kacperpe.zadania

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator

class Sfx(ctx: Context) {
    var sound = true
    var vib = true
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val ids = mapOf(
        "move" to pool.load(ctx, R.raw.move, 1),
        "capture" to pool.load(ctx, R.raw.capture, 1),
        "check" to pool.load(ctx, R.raw.check, 1),
        "ok" to pool.load(ctx, R.raw.ok, 1),
        "wrong" to pool.load(ctx, R.raw.wrong, 1),
    )
    private val vibrator: Vibrator? = ctx.getSystemService(Vibrator::class.java)

    fun play(name: String) {
        if (sound) ids[name]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun buzz(ms: Long) {
        if (!vib) return
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    fun release() = pool.release()
}
