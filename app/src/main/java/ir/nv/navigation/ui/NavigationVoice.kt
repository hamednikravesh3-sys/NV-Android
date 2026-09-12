package ir.nv.navigation.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun NavigationVoice(active: Boolean, enabled: Boolean, instruction: String?, safetyAlert: String?) {
    val context = LocalContext.current
    val speaker = remember { GuidanceSpeaker(context.applicationContext) }

    DisposableEffect(speaker) {
        onDispose { speaker.close() }
    }
    LaunchedEffect(active, enabled, instruction) {
        if (active && enabled && !instruction.isNullOrBlank()) {
            speaker.speakGuidance(instruction)
        } else if (!active || !enabled) {
            speaker.stop()
        }
    }
    LaunchedEffect(active, enabled, safetyAlert) {
        if (active && enabled && !safetyAlert.isNullOrBlank()) {
            speaker.speakAlert(safetyAlert)
        }
    }
}

internal class VoiceRepeatGate(
    private val guidanceRepeatWindowMillis: Long = 4_000L,
    private val alertRepeatWindowMillis: Long = 12_000L
) {
    private var lastGuidance: String? = null
    private var lastGuidanceAt: Long = Long.MIN_VALUE
    private var lastAlert: String? = null
    private var lastAlertAt: Long = Long.MIN_VALUE

    fun allowGuidance(text: String, nowMillis: Long): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        if (clean == lastGuidance && nowMillis - lastGuidanceAt < guidanceRepeatWindowMillis) return false
        lastGuidance = clean
        lastGuidanceAt = nowMillis
        return true
    }

    fun allowAlert(text: String, nowMillis: Long): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        if (clean == lastAlert && nowMillis - lastAlertAt < alertRepeatWindowMillis) return false
        lastAlert = clean
        lastAlertAt = nowMillis
        return true
    }

    fun reset() {
        lastGuidance = null
        lastGuidanceAt = Long.MIN_VALUE
        lastAlert = null
        lastAlertAt = Long.MIN_VALUE
    }
}

private class GuidanceSpeaker(context: Context) : TextToSpeech.OnInitListener, AutoCloseable {
    private val engine = TextToSpeech(context, this)
    private val repeatGate = VoiceRepeatGate()
    private var ready = false
    private var pending: Pending? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val persian = Locale("fa", "IR")
            val result = engine.setLanguage(persian)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.getDefault()
            }
            pending?.let { item ->
                pending = null
                speakInternal(item.text, item.alert)
            }
        }
    }

    fun speakGuidance(text: String) {
        val now = System.currentTimeMillis()
        if (!repeatGate.allowGuidance(text, now)) return
        if (!ready) {
            pending = Pending(text.trim(), false)
            return
        }
        speakInternal(text, false)
    }

    fun speakAlert(text: String) {
        val now = System.currentTimeMillis()
        if (!repeatGate.allowAlert(text, now)) return
        if (!ready) {
            pending = Pending(text.trim(), true)
            return
        }
        speakInternal(text, true)
    }

    private fun speakInternal(text: String, alert: Boolean) {
        engine.speak(
            text.trim(),
            if (alert) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
            null,
            if (alert) "nv-safety-alert" else "nv-guidance"
        )
    }

    fun stop() {
        pending = null
        repeatGate.reset()
        if (ready) engine.stop()
    }

    override fun close() {
        stop()
        engine.shutdown()
    }

    private data class Pending(val text: String, val alert: Boolean)
}
