package com.example.deskpet

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.util.concurrent.Executors

object Brain {
    @Volatile private var llm: LlmInference? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private fun modelFile(c: Context) = File(c.filesDir, "model.task")

    fun available(c: Context): Boolean = modelFile(c).let { it.exists() && it.length() > 0L }

    fun ask(c: Context, persona: String, user: String, onDone: (String) -> Unit) {
        val app = c.applicationContext
        worker.execute {
            val m = ensure(app)
            val answer = if (m == null) {
                "Модель не загружена. Открой приложение, раздел «Нейросеть», и нажми «Загрузить»."
            } else {
                val prompt = "<start_of_turn>user\n" + persona + "\n\n" + user +
                    "<end_of_turn>\n<start_of_turn>model\n"
                try { m.generateResponse(prompt).trim() } catch (e: Throwable) { "Ошибка: " + e.message }
            }
            ui.post { onDone(answer) }
        }
    }

    private fun ensure(c: Context): LlmInference? {
        llm?.let { return it }
        val f = modelFile(c)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val opts = LlmInferenceOptions.builder()
                .setModelPath(f.absolutePath)
                .setMaxTokens(1024)
                .build()
            val made = LlmInference.createFromOptions(c, opts)
            llm = made
            made
        } catch (e: Throwable) {
            null
        }
    }
}
