package com.example.deskpet

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// Видит только положение нажимаемых элементов (иконки, кнопки, ссылки). Тексты не читает.
class ScreenWatcher : AccessibilityService() {
    companion object {
        @Volatile var platforms: List<Rect> = emptyList()
        @Volatile var connected = false
    }

    private var lastScan = 0L
    private var visited = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        platforms = emptyList()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 350) return
        lastScan = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() == packageName) return
        val dm = resources.displayMetrics
        val out = ArrayList<Rect>()
        visited = 0
        collect(root, out, 0, dm.widthPixels, dm.heightPixels)
        platforms = out
    }

    private fun collect(n: AccessibilityNodeInfo, out: MutableList<Rect>, depth: Int, sw: Int, sh: Int) {
        if (out.size >= 150 || visited >= 800 || depth > 30) return
        visited++
        if (n.isVisibleToUser && (n.isClickable || n.isLongClickable)) {
            val r = Rect()
            n.getBoundsInScreen(r)
            val big = r.width() > sw * 0.95f && r.height() > sh * 0.4f
            if (r.width() >= 40 && r.height() >= 40 && !big && r.top > 0 && r.bottom < sh) out.add(r)
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collect(c, out, depth + 1, sw, sh)
        }
    }
}
