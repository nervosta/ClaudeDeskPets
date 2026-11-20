package com.example.deskpet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PetState { WALK, IDLE, SLEEP, HAPPY, JUMP }

class PetView(context: Context) : View(context) {
    var state = PetState.IDLE
    var facing = 1
    var kind = 0

    private val p = (5 * resources.displayMetrics.density).toInt()
    val size = 20 * p
    val pixelHeight = 22 * p

    private var t = 0f
    private var oy = 0
    private val paint = Paint().apply { isAntiAlias = false }

    private val white = 0xFFFFFFFF.toInt()
    private val pink = 0xFFE5566D.toInt()
    private val zz = 0xFF8D9BE0.toInt()
    // Клод
    private val cMain = 0xFFD97757.toInt()
    private val cShade = 0xFFC4673F.toInt()
    private val cDk = 0xFFB85C3E.toInt()
    private val cLine = 0xFF5A2D1E.toInt()
    private val cEye = 0xFF2A1F1A.toInt()
    private val cream = 0xFFFAF0E6.toInt()
    // Рекс
    private val gMain = 0xFF5BAE6A.toInt()
    private val gShade = 0xFF3E8A4F.toInt()
    private val gDk = 0xFF2F6B3D.toInt()
    private val gBelly = 0xFFD2EBB8.toInt()
    private val gEye = 0xFF1B2A1F.toInt()
    // Пёс
    private val dMain = 0xFFC8956A.toInt()
    private val dShade = 0xFFA9774E.toInt()
    private val dDk = 0xFF6B4528.toInt()
    private val dEye = 0xFF2A1B12.toInt()
    // Мопс
    private val fawn = 0xFFE3C48F.toInt()
    private val fawnSh = 0xFFC9A46B.toInt()
    private val mask = 0xFF3A2B22.toInt()
    // Летучка
    private val bBody = 0xFF5B4B8A.toInt()
    private val bDark = 0xFF3A2F5C.toInt()
    private val bWing = 0xFF7A68B0.toInt()
    private val bGlow = 0xFFFFD84D.toInt()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, pixelHeight)

    fun step() { t += 0.25f; invalidate() }

    private fun r(c: Canvas, x: Int, y: Int, w: Int, h: Int, col: Int) {
        val xx = if (facing < 0) 20 - x - w else x
        val dy = if (kind == 0) 2 else 0
        paint.color = col
        val top = (y + 6 + dy + oy) * p
        c.drawRect((xx * p).toFloat(), top.toFloat(), ((xx + w) * p).toFloat(), (top + h * p).toFloat(), paint)
    }

    override fun onDraw(c: Canvas) {
        val k = kind.coerceIn(0, 4)
        val up = state == PetState.HAPPY
        val walk = state == PetState.WALK || state == PetState.JUMP
        val sleep = state == PetState.SLEEP
        val fr = (t / 1.9f).toInt() % 2
        val bob = when {
            walk -> fr
            sleep -> (t / 5f).toInt() % 2
            up -> -(abs(sin(t * 0.8f)) * 3).roundToInt()
            else -> 0
        }
        when (k) {
            0 -> claude(c, up, walk, sleep, fr, bob)
            1 -> rex(c, up, walk, sleep, fr, bob)
            2 -> dog(c, up, walk, sleep, fr, bob)
            3 -> pug(c, up, walk, sleep, fr, bob)
            else -> bat(c, up, walk, sleep, fr, bob)
        }
        oy = bob
        if (sleep) {
            val zx = intArrayOf(14, 17, 16, 16, 14)[k]
            val zy = intArrayOf(-2, -2, 1, 1, -2)[k] - ((t % 12f) / 4f).toInt()
            r(c, zx, zy, 3, 1, zz); r(c, zx + 1, zy + 1, 1, 1, zz); r(c, zx, zy + 2, 3, 1, zz)
        }
        if (up) {
            val hx = intArrayOf(6, 13, 13, 13, 8)[k]
            val hy = intArrayOf(0, 0, 3, 3, 0)[k]
            r(c, hx + 1, -3 + hy, 1, 1, pink); r(c, hx + 3, -3 + hy, 1, 1, pink)
            r(c, hx, -2 + hy, 5, 1, pink); r(c, hx + 1, -1 + hy, 3, 1, pink); r(c, hx + 2, hy, 1, 1, pink)
        }
    }

    private fun claude(c: Canvas, up: Boolean, walk: Boolean, sleep: Boolean, fr: Int, bob: Int) {
        oy = bob
        fun q(x: Int, y: Int, w: Int, h: Int, col: Int) = r(c, x + 2, y, w, h, col)
        val legs = intArrayOf(4, 6, 9, 11)
        fun drawLegs() = legs.forEachIndexed { i, x ->
            val h = if (walk && ((i % 2 == 0) == (fr == 0))) 1 else 2
            q(x, 12, 1, h, cDk)
        }
        drawLegs()
        q(4, 2, 8, 1, cLine); q(3, 3, 10, 1, cLine); q(2, 4, 12, 7, cLine)
        q(3, 11, 10, 1, cLine); q(4, 12, 8, 1, cLine)
        q(4, 3, 8, 1, cMain); q(3, 4, 10, 6, cMain)
        q(3, 10, 10, 1, cShade); q(4, 11, 8, 1, cShade)
        drawLegs()
        val al = if (up) 4 else if (walk) 7 + fr else 7
        val ar = if (up) 4 else if (walk) 8 - fr else 7
        q(1, al, 2, 2, cDk); q(13, ar, 2, 2, cDk)
        val plus = (t / (if (up) 2f else 7f)).toInt() % 2 == 0
        if (plus) {
            q(8, 4, 1, 1, cream); q(7, 5, 3, 1, cream); q(8, 6, 1, 1, cream)
        } else {
            q(7, 4, 1, 1, cream); q(9, 4, 1, 1, cream); q(8, 5, 1, 1, cream)
            q(7, 6, 1, 1, cream); q(9, 6, 1, 1, cream)
        }
        for (x0 in intArrayOf(5, 9)) {
            val x = x0 + 1
            when {
                up -> { q(x, 9, 1, 1, cEye); q(x + 1, 8, 1, 1, cEye); q(x + 2, 9, 1, 1, cEye) }
                sleep || (t % 24f) < 1.5f -> q(x, 9, 2, 1, cEye)
                else -> q(x, 8, 2, 2, cEye)
            }
        }
    }

    private fun rex(c: Canvas, up: Boolean, walk: Boolean, sleep: Boolean, fr: Int, bob: Int) {
        oy = if (up) bob else 0
        val aH = if (walk && fr == 1) 2 else 3
        val bH = if (walk && fr == 0) 2 else 3
        r(c, 6, 13, 2, aH, gDk); r(c, 8, 12 + aH, 1, 1, gDk)
        r(c, 10, 13, 2, bH, gDk); r(c, 12, 12 + bH, 1, 1, gDk)
        oy = bob
        val tip = if (walk) fr else 0
        r(c, 4, 7, 2, 3, gMain); r(c, 2, 8, 2, 2, gMain); r(c, 0, 9 + tip, 2, 1, gShade)
        r(c, 6, 5, 1, 1, gDk); r(c, 8, 5, 1, 1, gDk); r(c, 10, 5, 1, 1, gDk)
        r(c, 5, 6, 8, 7, gMain)
        r(c, 5, 12, 4, 1, gShade)
        r(c, 9, 9, 4, 4, gBelly)
        r(c, 11, 4, 3, 3, gMain)
        r(c, 11, 2, 8, 5, gMain)
        r(c, 12, 1, 6, 1, gMain)
        r(c, 18, 3, 1, 1, gShade)
        if (up) {
            r(c, 13, 7, 4, 1, pink)
            r(c, 12, 8, 6, 1, gShade)
        } else {
            r(c, 12, 7, 6, 1, gShade)
            r(c, 15, 7, 1, 1, white); r(c, 17, 7, 1, 1, white)
        }
        when {
            up -> { r(c, 14, 4, 1, 1, gEye); r(c, 15, 3, 1, 1, gEye); r(c, 16, 4, 1, 1, gEye) }
            sleep || (t % 24f) < 1.5f -> r(c, 14, 4, 2, 1, gEye)
            else -> { r(c, 14, 3, 2, 2, gEye); r(c, 14, 3, 1, 1, white) }
        }
        if (up) { r(c, 13, 8, 2, 1, gDk); r(c, 14, 7, 1, 1, gDk) }
        else r(c, 13, 9, 2, 1, gDk)
    }

    private fun pug(c: Canvas, up: Boolean, walk: Boolean, sleep: Boolean, fr: Int, bob: Int) {
        oy = if (up) bob else 0
        intArrayOf(5, 7, 10, 12).forEachIndexed { i, x ->
            val h = if (walk && ((i % 2 == 0) == (fr == 0))) 2 else 3
            r(c, x, 13, 1, h, fawnSh)
        }
        oy = bob
        r(c, 3, 9, 1, 1, fawnSh); r(c, 2, 8, 1, 2, fawnSh); r(c, 3, 7 + fr, 1, 1, fawnSh)
        r(c, 4, 8, 10, 5, fawn)
        r(c, 4, 12, 10, 1, fawnSh)
        r(c, 12, 3, 7, 7, fawn)
        r(c, 12, 3, 2, 3, mask); r(c, 17, 3, 2, 2, mask)
        r(c, 14, 4, 4, 1, fawnSh)
        r(c, 16, 7, 3, 3, mask)
        when {
            up -> { r(c, 14, 6, 1, 1, mask); r(c, 15, 5, 1, 1, mask); r(c, 16, 6, 1, 1, mask) }
            sleep || (t % 24f) < 1.5f -> r(c, 15, 6, 2, 1, mask)
            else -> { r(c, 15, 5, 2, 2, mask); r(c, 15, 5, 1, 1, white) }
        }
        if (up) r(c, 17, 10, 1, 2, pink)
    }

    private fun bat(c: Canvas, up: Boolean, walk: Boolean, sleep: Boolean, fr: Int, bob: Int) {
        oy = if (up) bob else 0
        r(c, 8, 14, 1, 2, bDark); r(c, 11, 14, 1, 2, bDark)
        oy = bob
        if (sleep) {
            r(c, 5, 8, 2, 6, bWing); r(c, 13, 8, 2, 6, bWing)
        } else if (walk || up) {
            if (fr == 0) {
                r(c, 2, 8, 5, 2, bWing); r(c, 13, 8, 5, 2, bWing)
                r(c, 1, 10, 3, 1, bWing); r(c, 16, 10, 3, 1, bWing)
            } else {
                r(c, 2, 5, 5, 2, bWing); r(c, 13, 5, 5, 2, bWing)
                r(c, 1, 4, 3, 1, bWing); r(c, 16, 4, 3, 1, bWing)
            }
        } else {
            r(c, 3, 8, 4, 3, bWing); r(c, 13, 8, 4, 3, bWing)
            r(c, 2, 11, 3, 1, bWing); r(c, 15, 11, 3, 1, bWing)
        }
        r(c, 7, 4, 6, 10, bBody)
        r(c, 8, 10, 4, 4, bWing)
        r(c, 7, 2, 2, 2, bDark); r(c, 11, 2, 2, 2, bDark)
        when {
            up -> {
                r(c, 8, 6, 1, 1, bGlow); r(c, 9, 5, 1, 1, bGlow)
                r(c, 10, 5, 1, 1, bGlow); r(c, 11, 6, 1, 1, bGlow)
            }
            sleep || (t % 24f) < 1.5f -> { r(c, 8, 6, 1, 1, bDark); r(c, 11, 6, 1, 1, bDark) }
            else -> { r(c, 8, 5, 1, 2, bGlow); r(c, 11, 5, 1, 2, bGlow) }
        }
        r(c, 8, 8, 1, 1, white); r(c, 11, 8, 1, 1, white)
    }

    private fun dog(c: Canvas, up: Boolean, walk: Boolean, sleep: Boolean, fr: Int, bob: Int) {
        oy = if (up) bob else 0
        intArrayOf(4, 6, 10, 12).forEachIndexed { i, x ->
            val h = if (walk && ((i % 2 == 0) == (fr == 0))) 2 else 3
            r(c, x, 13, 1, h, dDk)
        }
        oy = bob
        if (sleep) {
            r(c, 2, 9, 2, 1, dMain)
        } else {
            if (fr == 0) r(c, 2, 5, 1, 3, dMain) else r(c, 3, 5, 1, 3, dMain)
            r(c, 3, 8, 1, 1, dMain)
        }
        r(c, 4, 8, 10, 5, dMain)
        r(c, 4, 12, 10, 1, dShade)
        r(c, 7, 9, 2, 2, dShade)
        r(c, 12, 4, 6, 5, dMain)
        r(c, 17, 6, 2, 3, dMain)
        r(c, 18, 6, 1, 1, dEye)
        r(c, 12, 4, 2, 4, dDk)
        r(c, 12, 8, 2, 2, pink)
        when {
            up -> { r(c, 14, 6, 1, 1, dEye); r(c, 15, 5, 1, 1, dEye); r(c, 16, 6, 1, 1, dEye) }
            sleep -> r(c, 14, 6, 2, 1, dEye)
            (t % 24f) < 1.5f -> r(c, 15, 6, 1, 1, dEye)
            else -> r(c, 15, 5, 1, 2, dEye)
        }
        r(c, 16, 8, 2, 1, dShade)
        if (up) r(c, 17, 9, 1, 2, pink)
    }
}
