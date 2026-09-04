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
            speaker.speak(instruction)
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

private class GuidanceSpeaker(context: Context) : TextToSpeech.OnInitListener, AutoCloseable {
    private val engine = TextToSpeech(context, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val result = engine.setLanguage(Locale("fa", "IR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.getDefault()
            }
            pending?.let {
                pending = null
                speak(it)
            }
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nv-guidance")
    }

    fun speakAlert(text: String) {
        if (!ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "nv-safety-alert")
    }

    fun stop() {
        pending = null
        if (ready) engine.stop()
    }

    override fun close() {
        engine.stop()
        engine.shutdown()
    }
}
