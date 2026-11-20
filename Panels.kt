package com.example.deskpet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

// Окошки-облака: меню, поиск файлов, заметки, нейросеть, голос.
internal fun PetService.showMenu() {
    val greeting = "Привет! Я $petName. Что сделаем?"
    val items = listOf(
        "🔎  Найти файл" to { showSearch("") },
        "📝  Заметки" to { showNotes() },
        "🧠  Спросить нейросеть" to { showAi("") },
        "🎤  Сказать команду" to { startVoice() },
        "✕  Закрыть" to { closeCloud() }
    )
    showCloud(false) { c ->
        val head = UI.text(this, 16f, true)
        c.addView(head)
        val views = items.map { (label, act) ->
            val v = UI.item(this, label) { act() }
            c.addView(v)
            v
        }
        Typer.prepare(head, greeting)
        views.forEachIndexed { i, v -> Typer.prepare(v, items[i].first) }
        fun typeItem(i: Int) {
            if (i >= views.size) return
            Typer.type(views[i], items[i].first, 16L) { typeItem(i + 1) }
        }
        Typer.type(head, greeting, 28L) { typeItem(0) }
    }
}

internal fun PetService.showMessage(msg: String) {
    showCloud(false) { c ->
        val t = UI.text(this, 15f)
        c.addView(t)
        Typer.prepare(t, msg)
        Typer.type(t, msg)
        c.addView(UI.row(this, UI.chip(this, "← Меню") { showMenu() }, UI.chip(this, "Закрыть") { closeCloud() }))
    }
}

// ---------------- поиск файлов ----------------
internal fun PetService.hasFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

internal fun PetService.openFilesAccessSettings() {
    try {
        val i = if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        else
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast("Открой приложение и выдай доступ к файлам")
    }
}

internal fun PetService.showSearch(initial: String) {
    val files = ArrayList<File>()
    val labels = ArrayList<String>()
    val searchId = AtomicInteger(0)
    val root = Environment.getExternalStorageDirectory()
    showCloud(true) { c ->
        val title = UI.text(this, 15f, true)
        c.addView(title)
        Typer.prepare(title, "Какой файл ищем?")
        Typer.type(title, "Какой файл ищем?")
        val input = UI.edit(this, "Название файла")
        c.addView(input)
        cloudFocus = input
        val info = UI.text(this, 12f)
        info.text = if (hasFilesAccess()) "Введи минимум 2 буквы" else "Нужен доступ ко всем файлам. Нажми сюда, чтобы разрешить."
        info.setOnClickListener { if (!hasFilesAccess()) openFilesAccessSettings() }
        c.addView(info)
        val adapter = UI.adapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
        val lv = ListView(this)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv.adapter = adapter
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            FileOps.open(this, files[pos])
            true
        }
        c.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (150 * d).toInt()))
        fun selected(): List<File> = files.indices.filter { lv.isItemChecked(it) }.map { files[it] }
        c.addView(
            UI.row(
                this,
                UI.chip(this, "Все") { for (i in files.indices) lv.setItemChecked(i, true) },
                UI.chip(this, "Копир.") { FileOps.copyNames(this, selected()) },
                UI.chip(this, "Отпр.") { FileOps.send(this, selected()) },
                UI.chip(this, "Править") {
                    val s = selected()
                    if (s.size == 1) FileOps.edit(this, s[0]) else toast("Выбери один файл")
                }
            )
        )
        c.addView(UI.row(this, UI.chip(this, "← Меню") { showMenu() }))

        fun doSearch(q: String) {
            val id = searchId.incrementAndGet()
            if (!hasFilesAccess()) {
                info.text = "Нужен доступ ко всем файлам. Нажми сюда, чтобы разрешить."
                return
            }
            if (q.length < 2) {
                files.clear(); labels.clear()
                adapter.notifyDataSetChanged()
                info.text = "Введи минимум 2 буквы"
                return
            }
            info.text = "Ищу…"
            Thread {
                val found = FileOps.search(root, q, 100) { id != searchId.get() }
                handler.post {
                    if (id == searchId.get()) {
                        files.clear(); files.addAll(found); labels.clear()
                        for (f in found) {
                            val where = f.parentFile?.path?.removePrefix(root.path) ?: ""
                            labels.add(f.name + "\n" + (if (where.isEmpty()) "/" else where))
                        }
                        lv.clearChoices()
                        adapter.notifyDataSetChanged()
                        info.text = if (found.isEmpty()) "Ничего не найдено" else "Найдено: " + found.size
                    }
                }
            }.start()
        }

        var pending: Runnable? = null
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let { handler.removeCallbacks(it) }
                val q = s.toString().trim()
                val r = Runnable { doSearch(q) }
                pending = r
                handler.postDelayed(r, 400)
            }
        })
        if (initial.isNotBlank()) input.setText(initial)
    }
}

// ---------------- заметки ----------------
internal fun PetService.showNotes() {
    val notes = Notes.load(this)
    showCloud(true) { c ->
        val title = UI.text(this, 15f, true)
        c.addView(title)
        Typer.prepare(title, "Твои заметки")
        Typer.type(title, "Твои заметки")
        val input = UI.edit(this, "Новая заметка")
        c.addView(input)
        cloudFocus = input
        val adapter = UI.adapter(this, android.R.layout.simple_list_item_1, notes)
        val lv = ListView(this)
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("note", notes[pos]))
            toast("Скопировано")
        }
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            notes.removeAt(pos)
            Notes.save(this, notes)
            adapter.notifyDataSetChanged()
            true
        }
        c.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (140 * d).toInt()))
        fun add() {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                notes.add(0, t)
                Notes.save(this, notes)
                adapter.notifyDataSetChanged()
                input.setText("")
            }
        }
        c.addView(UI.row(this, UI.chip(this, "Добавить") { add() }, UI.chip(this, "← Меню") { showMenu() }))
    }
}

// ---------------- нейросеть ----------------
internal fun PetService.showAi(initial: String) {
    showCloud(true) { c ->
        val title = UI.text(this, 15f, true)
        c.addView(title)
        Typer.prepare(title, "Спроси меня о чём угодно")
        Typer.type(title, "Спроси меня о чём угодно")
        val scroll = ScrollView(this)
        val answer = UI.text(this, 14f)
        scroll.addView(answer)
        c.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (110 * d).toInt()))
        val input = UI.edit(this, "Вопрос")
        c.addView(input)
        cloudFocus = input
        fun ask(q: String) {
            if (q.isBlank()) return
            input.setText("")
            answer.text = "Думаю…"
            val persona = "Ты — маленький пиксельный питомец по имени $petName на экране телефона. " +
                "Отвечай по-русски, коротко (1-3 предложения), дружелюбно."
            Brain.ask(this, persona, q) { a -> Typer.type(answer, a, 18L) }
        }
        c.addView(
            UI.row(
                this,
                UI.chip(this, "Спросить") { ask(input.text.toString().trim()) },
                UI.chip(this, "← Меню") { showMenu() }
            )
        )
        if (!Brain.available(this)) {
            answer.text = "Модель не загружена. Открой приложение DeskPets Cloud, раздел «Нейросеть», и нажми «Загрузить»."
        } else if (initial.isNotBlank()) {
            ask(initial)
        }
    }
}

// ---------------- голос ----------------
internal fun PetService.startVoice() {
    closeCloud()
    VoiceActivity.callback = { text ->
        handler.post {
            if (text == null) showMessage("Не расслышал. Нажми на меня и попробуй ещё раз.")
            else handleVoice(text)
        }
    }
    try {
        startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast("Не удалось запустить голосовой ввод")
    }
}

internal fun PetService.handleVoice(raw: String) {
    val s = raw.lowercase().trim()
        .replace(Regex("^(клод|рекс|пес|пёс|мопс|летучка)[,!.]?\\s*"), "")
    val noteWords = Regex("^(запиши|создай заметк|добавь заметк|заметк)")
    val findWords = Regex("^(найди|найти|ищи|поищи|поиск|покажи|открой)\\s")
    when {
        noteWords.containsMatchIn(s) -> {
            val text = s.replace(Regex("^(запиши|создай заметк\\w*|добавь заметк\\w*|заметк\\w*)[:,]?\\s*"), "").trim()
            if (text.isEmpty()) {
                showNotes()
            } else {
                Notes.add(this, text)
                showMessage("Записал: $text")
            }
        }
        findWords.containsMatchIn(s) -> {
            val q = s.replace(Regex("^(найди|найти|ищи|поищи|поиск|покажи|открой)\\s+(?:файл\\w*\\s+)?"), "").trim()
            showSearch(q)
        }
        else -> showAi(s)
    }
}
