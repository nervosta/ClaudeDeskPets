package com.example.deskpet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private val names = arrayOf("Клод", "Рекс", "Пёс", "Мопс", "Летучка")
    private lateinit var vision: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "DeskPets Cloud\nНажми на питомца, чтобы открыть облако с меню. Удерживай, чтобы сказать команду голосом."
            textSize = 18f
            gravity = Gravity.CENTER
        })
        val current = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        fun refresh() { current.text = "Питомец: " + names[prefs.getInt("kind", 0).coerceIn(0, names.size - 1)] }
        root.addView(current)
        names.forEachIndexed { i, n ->
            root.addView(Button(this).apply {
                text = n
                setOnClickListener { prefs.edit().putInt("kind", i).apply(); refresh() }
            })
        }
        refresh()
        root.addView(Button(this).apply {
            text = "Запустить питомца"
            setOnClickListener { startPet() }
        })
        root.addView(Button(this).apply {
            text = "Убрать питомца"
            setOnClickListener { stopService(Intent(this@MainActivity, PetService::class.java)) }
        })
        vision = TextView(this).apply { textSize = 15f; gravity = Gravity.CENTER; setPadding(0, pad / 2, 0, 0) }
        root.addView(vision)
        root.addView(Button(this).apply {
            text = "Включить зрение питомца"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "Доступ ко всем файлам"
            setOnClickListener { openFiles() }
        })
        root.addView(Button(this).apply {
            text = "Нейросеть (загрузка модели)"
            setOnClickListener { startActivity(Intent(this@MainActivity, AiActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "Найти файл (большое окно)"
            setOnClickListener { startActivity(Intent(this@MainActivity, SearchActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "Заметки (большое окно)"
            setOnClickListener { startActivity(Intent(this@MainActivity, NotesActivity::class.java)) }
        })
        root.addView(TextView(this).apply {
            text = "Чтобы питомец видел иконки и ссылки: Настройки → Специальные возможности → Установленные приложения → DeskPets Cloud → включить. " +
                "Если пункт недоступен: Настройки → Приложения → DeskPets Cloud → меню ⋮ → «Разрешить ограниченные настройки»."
            textSize = 13f
            setPadding(0, pad / 2, 0, 0)
        })
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        vision.text = if (ScreenWatcher.connected) "Зрение питомца: включено" else "Зрение питомца: выключено"
    }

    private fun openFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
        }
    }

    private fun startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разреши показ поверх других приложений и вернись", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        ContextCompat.startForegroundService(this, Intent(this, PetService::class.java))
    }
}
