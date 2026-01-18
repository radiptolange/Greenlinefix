package com.example.amoled_fix

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class OverlayService : AccessibilityService() {

    private lateinit var windowManager: WindowManager

    // Data class for Line Configuration
    data class LineConfig(
        var x: Int = 0,
        var width: Int = 5,
        var visible: Boolean = true
    )

    // Map ID -> View
    private val activeLines = mutableMapOf<String, View>()
    // Map ID -> Config
    private val lineConfigs = mutableMapOf<String, LineConfig>()

    private var selectedLineId: String? = null

    companion object {
        var instance: OverlayService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        restoreState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeAllViews()
    }

    // --- Public API ---

    fun getLineConfigsMap(): Map<String, Map<String, Any>> {
        val result = mutableMapOf<String, Map<String, Any>>()
        lineConfigs.forEach { (id, config) ->
            result[id] = mapOf(
                "x" to config.x,
                "width" to config.width,
                "visible" to config.visible
            )
        }
        return result
    }

    fun setLineConfigsMap(configs: Map<String, Map<String, Any>>) {
        removeAllLinesInternal()
        configs.forEach { (id, data) ->
            val x = (data["x"] as? Int) ?: 0
            val width = (data["width"] as? Int) ?: 5
            val visible = (data["visible"] as? Boolean) ?: true
            addLineInternal(id, x, width, visible)
        }
        // Select last one by default if any
        if (activeLines.isNotEmpty()) {
            selectedLineId = activeLines.keys.last()
        }
        saveState()
    }

    fun addLine(id: String, x: Int = 0, width: Int = 5, visible: Boolean = true) {
        addLineInternal(id, x, width, visible)
        saveState()
    }

    private fun addLineInternal(id: String, x: Int, width: Int, visible: Boolean) {
        if (lineConfigs.containsKey(id)) return

        val config = LineConfig(x, width, visible)
        lineConfigs[id] = config

        if (visible) {
            createLineView(id, config)
        }
        selectedLineId = id
    }

    fun removeLine(id: String) {
        val view = activeLines[id]
        if (view != null) {
            windowManager.removeView(view)
            activeLines.remove(id)
        }
        lineConfigs.remove(id)
        if (selectedLineId == id) {
            selectedLineId = activeLines.keys.firstOrNull()
        }
        saveState()
    }

    fun removeAllLines() {
        removeAllLinesInternal()
        saveState()
    }

    private fun removeAllLinesInternal() {
        activeLines.values.forEach { windowManager.removeView(it) }
        activeLines.clear()
        lineConfigs.clear()
        selectedLineId = null
    }

    fun toggleLine(id: String, visible: Boolean) {
        val config = lineConfigs[id] ?: return
        config.visible = visible

        if (visible) {
            if (!activeLines.containsKey(id)) {
                createLineView(id, config)
            }
        } else {
            val view = activeLines[id]
            if (view != null) {
                windowManager.removeView(view)
                activeLines.remove(id)
            }
        }
        saveState()
    }

    fun selectLine(id: String) {
        if (lineConfigs.containsKey(id)) {
            selectedLineId = id
        }
        // Selection is transient, maybe don't save?
        // But user might expect it. Let's not save selection to avoid complex restore logic for now.
    }

    fun updateLineWidth(id: String, width: Int) {
         val config = lineConfigs[id] ?: return
         config.width = width
         val view = activeLines[id]
         if (view != null) {
             val params = view.layoutParams as WindowManager.LayoutParams
             params.width = width
             windowManager.updateViewLayout(view, params)
         }
         saveState()
    }

    fun moveSelectedLine(deltaX: Int) {
        val id = selectedLineId ?: return
        val view = activeLines[id] ?: return
        val config = lineConfigs[id] ?: return

        val params = view.layoutParams as WindowManager.LayoutParams
        config.x += deltaX
        params.x = config.x
        windowManager.updateViewLayout(view, params)
        saveState()
    }

    fun resetSelectedLine() {
        val id = selectedLineId ?: return
        val view = activeLines[id] ?: return
        val config = lineConfigs[id] ?: return

        config.x = 0
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = 0
        windowManager.updateViewLayout(view, params)
        saveState()
    }

    // --- Private ---

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            metrics.bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            metrics.heightPixels
        }
    }

    private fun createLineView(id: String, config: LineConfig) {
        try {
            val lineView = View(this)
            lineView.setBackgroundColor(Color.BLACK)

            val screenHeight = getScreenHeight() + 200

            val params = WindowManager.LayoutParams(
                config.width,
                screenHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.OPAQUE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            params.alpha = 1.0f
            params.dimAmount = 0.0f

            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = config.x
            params.y = -100

            windowManager.addView(lineView, params)
            activeLines[id] = lineView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeAllViews() {
        removeAllLinesInternal() // Don't save empty state on destroy, just clear view
    }

    // --- Persistence ---

    private fun saveState() {
        val prefs = getSharedPreferences("overlay_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()
        val ids = lineConfigs.keys
        editor.putStringSet("line_ids", ids)
        ids.forEach { id ->
            val config = lineConfigs[id]!!
            editor.putInt("line_${id}_x", config.x)
            editor.putInt("line_${id}_width", config.width)
            editor.putBoolean("line_${id}_visible", config.visible)
        }
        editor.apply()
    }

    private fun restoreState() {
        val prefs = getSharedPreferences("overlay_prefs", MODE_PRIVATE)
        val ids = prefs.getStringSet("line_ids", null) ?: return
        ids.forEach { id ->
            val x = prefs.getInt("line_${id}_x", 0)
            val width = prefs.getInt("line_${id}_width", 5)
            val visible = prefs.getBoolean("line_${id}_visible", true)
            addLineInternal(id, x, width, visible)
        }
        if (activeLines.isNotEmpty()) {
            selectedLineId = activeLines.keys.last()
        }
    }
}
