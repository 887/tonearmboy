package com.eight87.tonearmboy.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * Process-wide accelerometer-based shake detector used by the sleep
 * timer's "pick the phone up to resume" behaviour. After
 * [SleepTimer] fires + pauses, the app graph calls [arm] with a
 * resume callback; the next time the user shakes (or sharply picks
 * up) the phone within [armWindowMs] the callback fires and the
 * listener self-disarms.
 *
 * Threshold tuned to be triggered by a deliberate pick-up motion
 * (~15 m/s² peak total acceleration above gravity, with a short
 * cooldown so multiple jolts in a row don't re-fire). Lower than a
 * vigorous shake on purpose — the user has already chosen the
 * "wake on pickup" affordance, false positives from a bedside
 * adjustment cost less than missing the deliberate gesture.
 */
class ShakeWaker(context: Context) : SensorEventListener {

  private val sensorManager: SensorManager? =
    context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val accelerometer: Sensor? =
    sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  @Volatile private var onShake: (() -> Unit)? = null
  @Volatile private var armedAtMs: Long = 0L

  /**
   * Arm the detector for [armWindowMs] ms. Subsequent shake events
   * fire [onShake] exactly once and disarm the listener. Re-arming
   * before the window expires replaces the previous callback.
   */
  fun arm(armWindowMs: Long = DEFAULT_ARM_WINDOW_MS, onShake: () -> Unit) {
    if (sensorManager == null || accelerometer == null) return
    this.onShake = onShake
    this.armedAtMs = SystemClock.elapsedRealtime()
    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    // Auto-disarm after the window even if no shake fires, so the
    // accelerometer doesn't stay on indefinitely (battery cost).
    val expectedArmedAt = armedAtMs
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
      .launch {
        kotlinx.coroutines.delay(armWindowMs)
        if (armedAtMs == expectedArmedAt) disarm()
      }
  }

  fun disarm() {
    onShake = null
    armedAtMs = 0L
    sensorManager?.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (onShake == null) return
    val x = event.values[0]
    val y = event.values[1]
    val z = event.values[2]
    // Total acceleration magnitude, minus gravity (~9.81 m/s²).
    val gForce = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
    if (gForce > SHAKE_THRESHOLD_MPS2) {
      val cb = onShake ?: return
      disarm()
      cb()
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  companion object {
    /** Default time the listener stays armed after the timer fires. */
    const val DEFAULT_ARM_WINDOW_MS: Long = 10 * 60 * 1000L
    /** Peak-above-gravity threshold (m/s²) that counts as a pick-up. */
    const val SHAKE_THRESHOLD_MPS2: Float = 5f
  }
}
