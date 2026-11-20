package com.example.deskpet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

// Облачко-меню, которое отходит от питомца.
class CloudView(ctx: Context, private val tailOnLeft: Boolean) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val tail = 24 * d
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFBF2.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A2D1E.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
    }
    var tailY = 40 * d
    var onBack: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        val p = (14 * d).toInt()
        val t = tail.toInt()
        setPadding(if (tailOnLeft) p + t else p, p, if (tailOnLeft) p else p + t, p)
    }

    override fun onDraw(c: Canvas) {
        val l = if (tailOnLeft) tail else 0f
        val r = if (tailOnLeft) width.toFloat() else width - tail
        val rect = RectF(l + 2 * d, 2 * d, r - 2 * d, height - 2 * d)
        c.drawRoundRect(rect, 24 * d, 24 * d, fill)
        c.drawRoundRect(rect, 24 * d, 24 * d, line)
        val dir = if (tailOnLeft) -1f else 1f
        val edge = if (tailOnLeft) l else r
        val y = tailY.coerceIn(24 * d, (height - 24 * d).coerceAtLeast(24 * d))
        val centers = floatArrayOf(6f, 15f, 21f)
        val radii = floatArrayOf(6f, 4f, 2.5f)
        for (i in 0..2) {
            val cx = edge + dir * centers[i] * d
            c.drawCircle(cx, y, radii[i] * d, fill)
            c.drawCircle(cx, y, radii[i] * d, line)
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) {
            onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(e)
    }
}

// Эффект печатной машинки. Место под текст резервируется сразу (буквы прозрачные).
object Typer {
    private val h = Handler(Looper.getMainLooper())
    private var gen = 0

    fun cancelAll() {
        gen++
        h.removeCallbacksAndMessages(null)
    }

    private fun show(tv: TextView, text: String, n: Int) {
        val s = SpannableString(text)
        if (n < text.length) {
            s.setSpan(ForegroundColorSpan(Color.TRANSPARENT), n, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tv.text = s
    }

    fun prepare(tv: TextView, text: String) = show(tv, text, 0)

    fun type(tv: TextView, text: String, speed: Long = 26L, done: (() -> Unit)? = null) {
        val my = gen
        val token = Any()
        tv.tag = token
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (my != gen || tv.tag !== token) return
                i++
                show(tv, text, i)
                if (i < text.length) h.postDelayed(this, speed) else done?.invoke()
            }
        }
        show(tv, text, 0)
        h.postDelayed(step, speed)
    }
}

object UI {
    val INK = 0xFF2B1D14.toInt()
    val SOFT = 0xFF7A6A5C.toInt()
    val PILL = 0xFFF3E3CF.toInt()
    val ACCENT = 0xFFD97757.toInt()

    private fun dp(c: Context, v: Float) = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, size: Float = 15f, bold: Boolean = false): TextView = TextView(c).apply {
        textSize = size
        setTextColor(INK)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(c, 3f), 0, dp(c, 3f))
    }

    fun item(c: Context, label: String, onClick: () -> Unit): TextView = TextView(c).apply {
        textSize = 15f
        setTextColor(INK)
        text = label
        background = GradientDrawable().apply {
            setColor(PILL)
            cornerRadius = dp(c, 14f).toFloat()
        }
        setPadding(dp(c, 12f), dp(c, 9f), dp(c, 12f), dp(c, 9f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, 6f) }
        setOnClickListener { onClick() }
    }

    fun chip(c: Context, label: String, onClick: () -> Unit): TextView = TextView(c).apply {
        textSize = 12f
        setTextColor(Color.WHITE)
        text = label
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(ACCENT)
            cornerRadius = dp(c, 12f).toFloat()
        }
        setPadding(dp(c, 6f), dp(c, 8f), dp(c, 6f), dp(c, 8f))
        setOnClickListener { onClick() }
    }

    fun row(c: Context, vararg chips: TextView): LinearLayout {
        val r = LinearLayout(c)
        r.orientation = LinearLayout.HORIZONTAL
        chips.forEachIndexed { i, ch ->
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(c, 6f)
            lp.topMargin = dp(c, 8f)
            r.addView(ch, lp)
        }
        return r
    }

    fun edit(c: Context, hint: String): EditText = EditText(c).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(INK)
        setHintTextColor(SOFT)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT
    }

    fun adapter(c: Context, layout: Int, items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(c, layout, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.apply {
                    setTextColor(INK)
                    textSize = 13f
                    maxLines = 2
                }
                return v
            }
        }
}

object FileOps {
    fun mime(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    fun uri(c: Context, f: File): Uri =
        FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f)

    private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

    fun open(c: Context, f: File) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri(c, f), mime(f))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast(c, "Не удалось открыть файл")
        }
    }

    fun edit(c: Context, f: File) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_EDIT).setDataAndType(uri(c, f), mime(f))
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    )
            )
        } catch (e: Exception) {
            open(c, f)
        }
    }

    fun send(c: Context, files: List<File>) {
        if (files.isEmpty()) { toast(c, "Сначала выбери файлы"); return }
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).setType(mime(files[0])).putExtra(Intent.EXTRA_STREAM, uri(c, files[0]))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(c, it) }))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            c.startActivity(Intent.createChooser(intent, "Отправить").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(c, "Не удалось отправить")
        }
    }

    fun copyNames(c: Context, files: List<File>) {
        if (files.isEmpty()) { toast(c, "Сначала выбери файлы"); return }
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("files", files.joinToString("\n") { it.name }))
        toast(c, "Скопировано названий: " + files.size)
    }

    fun search(root: File, query: String, limit: Int, cancelled: () -> Boolean): List<File> {
        val found = ArrayList<File>()
        val q = query.lowercase()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty() && found.size < limit && !cancelled()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) stack.add(f)
                } else if (f.name.lowercase().contains(q)) {
                    found.add(f)
                }
            }
        }
        found.sortByDescending { it.lastModified() }
        return found
    }
}
