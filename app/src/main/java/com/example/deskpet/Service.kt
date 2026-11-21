package com.example.deskpet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class PetService : Service() {
    private enum class Mode { GROUND, HOP, FALL, DRAG }

    private lateinit var wm: WindowManager
    private lateinit var pet: PetView
    private lateinit var lp: WindowManager.LayoutParams
    internal val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("pet", Context.MODE_PRIVATE) }
    internal val d: Float by lazy { resources.displayMetrics.density }
    private val names = arrayOf("Клод", "Рекс", "Пёс", "Мопс", "Летучка")
    internal val petName: String get() = names[prefs.getInt("kind", 0).coerceIn(0, 4)]

    private var mode = Mode.GROUND
    private var stateUntil = 0L
    private var frozen = false
    private var vy = 0f
    private var hopStart = 0L
    private var hopDur = 600L
    private var hx0 = 0
    private var hy0 = 0
    private var hx1 = 0
    private var hy1 = 0
    private var hopArc = 0f

    private var cloud: CloudView? = null
    internal var cloudFocus: EditText? = null

    private val loop = object : Runnable {
        override fun run() { tick(); handler.postDelayed(this, 33) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        pet = PetView(this)
        val dm = resources.displayMetrics
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dm.widthPixels / 3
        lp.y = floorTop() - pet.pixelHeight
        pet.setOnTouchListener(touch())
        wm.addView(pet, lp)
        handler.post(loop)
    }

    private fun floorTop(): Int = resources.displayMetrics.heightPixels - (100 * d).toInt()

    internal fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------- касания ----------------
    private fun touch(): View.OnTouchListener {
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false
        var longFired = false
        val longPress = Runnable {
            if (!moved && mode == Mode.GROUND) {
                longFired = true
                startVoice()
            }
        }
        return View.OnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y
                    moved = false; longFired = false
                    handler.postDelayed(longPress, 650)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(e.rawX - sx) > 10 * d || abs(e.rawY - sy) > 10 * d)) {
                        moved = true
                        handler.removeCallbacks(longPress)
                        closeCloud()
                        mode = Mode.DRAG
                        pet.state = PetState.IDLE
                    }
                    if (moved) {
                        lp.x = ox + (e.rawX - sx).toInt()
                        lp.y = oy + (e.rawY - sy).toInt()
                        wm.updateViewLayout(pet, lp)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (moved) {
                        stateUntil = 0L
                        leaveGround(platforms())
                    } else if (!longFired && e.action == MotionEvent.ACTION_UP) {
                        onTap()
                    }
                }
            }
            true
        }
    }

    private fun onTap() {
        if (mode == Mode.HOP || mode == Mode.FALL) return
        if (cloud != null) { closeCloud(); return }
        pet.state = PetState.HAPPY
        stateUntil = SystemClock.uptimeMillis() + 1600
        showMenu()
    }

    // ---------------- движение ----------------
    private fun platforms(): List<Rect> {
        val dm = resources.displayMetrics
        val list = ArrayList<Rect>(ScreenWatcher.platforms)
        val ft = floorTop()
        list.add(Rect(0, ft, dm.widthPixels, ft + 4))
        return list
    }

    private fun tick() {
        pet.kind = prefs.getInt("kind", 0)
        val now = SystemClock.uptimeMillis()
        if (frozen) {
            if (pet.state == PetState.HAPPY && now >= stateUntil) pet.state = PetState.IDLE
            pet.step()
            return
        }
        val plats = platforms()
        when (mode) {
            Mode.DRAG -> {}
            Mode.HOP -> updateHop(now)
            Mode.FALL -> updateFall(plats, now)
            Mode.GROUND -> updateGround(now, plats)
        }
        pet.step()
    }

    private fun updateGround(now: Long, plats: List<Rect>) {
        val feet = lp.y + pet.pixelHeight
        val cx = lp.x + pet.size / 2
        val sup = plats.firstOrNull { abs(it.top - feet) <= 14 * d && cx >= it.left && cx <= it.right }
        if (sup == null) {
            leaveGround(plats)
            return
        }
        if (lp.y != sup.top - pet.pixelHeight) {
            lp.y = sup.top - pet.pixelHeight
            wm.updateViewLayout(pet, lp)
        }
        if (now >= stateUntil) {
            when (Random.nextInt(10)) {
                in 0..4 -> {
                    pet.state = PetState.WALK
                    stateUntil = now + Random.nextLong(2500, 6000)
                    pet.facing = if (Random.nextBoolean()) 1 else -1
                }
                in 5..6 -> {
                    pet.state = PetState.IDLE
                    stateUntil = now + Random.nextLong(1500, 3500)
                }
                7 -> {
                    pet.state = PetState.SLEEP
                    stateUntil = now + Random.nextLong(5000, 10000)
                }
                else -> {
                    if (!startHop(plats)) {
                        pet.state = PetState.IDLE
                        stateUntil = now + 1500
                    }
                    return
                }
            }
        }
        if (pet.state == PetState.WALK) {
            lp.x += pet.facing * (d * 2.5f).toInt()
            val ncx = lp.x + pet.size / 2
            if (ncx < sup.left) pet.facing = 1
            if (ncx > sup.right) pet.facing = -1
            wm.updateViewLayout(pet, lp)
        }
    }

    private fun leaveGround(plats: List<Rect>) {
        if (pet.kind == 4) {
            if (!startHop(plats)) startFall()
        } else {
            startFall()
        }
    }

    private fun startFall() {
        mode = Mode.FALL
        vy = 0f
        pet.state = PetState.JUMP
    }

    private fun startHop(plats: List<Rect>): Boolean {
        val dm = resources.displayMetrics
        val bat = pet.kind == 4
        val feet = lp.y + pet.pixelHeight
        val cx = lp.x + pet.size / 2
        val maxDx = if (bat) dm.widthPixels.toFloat() else 380 * d
        val maxUp = if (bat) dm.heightPixels.toFloat() else 280 * d
        val minTop = (60 * d).toInt() + pet.pixelHeight
        val cands = plats.filter {
            val same = abs(it.top - feet) <= 14 * d && cx >= it.left && cx <= it.right
            !same && it.top >= minTop && abs(it.exactCenterX() - cx) <= maxDx && (feet - it.top) <= maxUp
        }
        if (cands.isEmpty()) return false
        val pick = cands.sortedBy { abs(it.exactCenterX() - cx) + abs(it.top - feet) }.take(4).random()
        val tx = pick.left + (pick.width() * (0.2f + Random.nextFloat() * 0.6f)).toInt()
        hx0 = lp.x
        hy0 = lp.y
        hx1 = tx - pet.size / 2
        hy1 = pick.top - pet.pixelHeight
        val dist = hypot((hx1 - hx0).toFloat(), (hy1 - hy0).toFloat())
        hopDur = (420 + dist / d * 1.1f).toLong().coerceIn(420L, 1300L)
        hopArc = (if (bat) 30 * d else 70 * d) + maxOf(0, hy0 - hy1) * 0.35f
        hopStart = SystemClock.uptimeMillis()
        pet.facing = if (hx1 >= hx0) 1 else -1
        pet.state = PetState.JUMP
        mode = Mode.HOP
        return true
    }

    private fun updateHop(now: Long) {
        val e = ((now - hopStart).toFloat() / hopDur).coerceIn(0f, 1f)
        lp.x = (hx0 + (hx1 - hx0) * e).toInt()
        var y = hy0 + (hy1 - hy0) * e - hopArc * 4f * e * (1f - e)
        if (pet.kind == 4) y += sin(e * 3.1415927f * 3f) * 8f * d
        lp.y = y.toInt()
        if (e >= 1f) {
            lp.x = hx1
            lp.y = hy1
            mode = Mode.GROUND
            pet.state = PetState.IDLE
            stateUntil = now + Random.nextLong(600, 1500)
        }
        wm.updateViewLayout(pet, lp)
    }

    private fun updateFall(plats: List<Rect>, now: Long) {
        val prevFeet = lp.y + pet.pixelHeight
        vy += 0.7f * d
        lp.y += vy.toInt()
        val feet = lp.y + pet.pixelHeight
        val cx = lp.x + pet.size / 2
        val land = plats
            .filter { cx >= it.left && cx <= it.right && prevFeet <= it.top + 6 && feet >= it.top }
            .minByOrNull { it.top }
        if (land != null) {
            lp.y = land.top - pet.pixelHeight
            vy = 0f
            mode = Mode.GROUND
            pet.state = PetState.IDLE
            stateUntil = now + 600
        }
        wm.updateViewLayout(pet, lp)
    }

    // ---------------- облако ----------------
    internal fun showCloud(keyboard: Boolean, build: (CloudView) -> Unit) {
        closeCloud()
        frozen = true
        val dm = resources.displayMetrics
        val petCx = lp.x + pet.size / 2
        val toRight = petCx < dm.widthPixels / 2
        val maxW = (300 * d).toInt()
        val gap = (6 * d).toInt()
        val margin = (8 * d).toInt()
        var cx0: Int
        var cw: Int
        if (toRight) {
            cx0 = lp.x + pet.size - gap
            cw = minOf(maxW, dm.widthPixels - cx0 - margin)
        } else {
            val rEdge = lp.x + gap
            cw = minOf(maxW, rEdge - margin)
            cx0 = rEdge - cw
        }
        if (cw < (180 * d).toInt()) {
            cw = (180 * d).toInt()
            cx0 = cx0.coerceIn(0, dm.widthPixels - cw)
        }
        val view = CloudView(this, toRight)
        view.onBack = { closeCloud() }
        cloudFocus = null
        build(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val ch = view.measuredHeight
        val limitBottom = if (keyboard) (dm.heightPixels * 0.5f).toInt() else dm.heightPixels - (24 * d).toInt()
        val minTop = (40 * d).toInt()
        val top = (lp.y + pet.pixelHeight / 2 - ch / 2).coerceAtMost(limitBottom - ch).coerceAtLeast(minTop)
        view.tailY = (lp.y + pet.pixelHeight / 2 - top).toFloat()
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!keyboard) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val clp = WindowManager.LayoutParams(
            cw,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        clp.gravity = Gravity.TOP or Gravity.START
        clp.x = cx0
        clp.y = top
        if (keyboard) clp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        wm.addView(view, clp)
        cloud = view
        val focus = cloudFocus
        if (focus != null) {
            focus.post {
                focus.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    internal fun closeCloud() {
        Typer.cancelAll()
        val v = cloud
        if (v != null) {
            try { wm.removeView(v) } catch (e: Exception) {}
        }
        cloud = null
        cloudFocus = null
        frozen = false
    }

    // ---------------- служба ----------------
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pet", "DeskPets", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, PetService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "pet")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Питомец гуляет")
            .addAction(Notification.Action.Builder(null, "Убрать", stop).build())
            .build()
        ServiceCompat.startForeground(
            this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        closeCloud()
        if (::pet.isInitialized) wm.removeView(pet)
        super.onDestroy()
    }
}
