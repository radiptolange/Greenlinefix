package com.example.amoled_fix

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class OverlayService : AccessibilityService() {

    private lateinit var windowManager: WindowManager

    companion object {
        const val ACTION_START = "com.example.amoled_fix.ACTION_START"

        @Volatile
        var instance: OverlayService? = null
            private set
    }

    data class LineConfig(
        var x: Int = 0,
        var width: Int = 5,
        var visible: Boolean = true
    )

    private val activeLines = mutableMapOf<String, View>()
    private val lineConfigs = mutableMapOf<String, LineConfig>()
    private var selectedLineId: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        restoreState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeAllViews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAllLineLayouts()
    }

    // --- Public API ---

    fun getLineConfigsMap(): Map<String, Map<String, Any>> {
        return lineConfigs.mapValues { (_, config) ->
            mapOf("x" to config.x, "width" to config.width, "visible" to config.visible)
        }
    }

    fun setLineConfigsMap(configs: Map<String, Map<String, Any>>) {
        removeAllLinesInternal()
        configs.forEach { (id, data) ->
            val x = (data["x"] as? Int) ?: 0
            val width = (data["width"] as? Int) ?: 5
            val visible = (data["visible"] as? Boolean) ?: true
            addLineInternal(id, x, width, visible)
        }
        if (activeLines.isNotEmpty()) selectedLineId = activeLines.keys.last()
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
        selectedLineId = id
        if (visible) createLineView(id, config)
    }

    fun removeLine(id: String) {
        activeLines[id]?.let { windowManager.removeView(it) }
        activeLines.remove(id)
        lineConfigs.remove(id)
        if (selectedLineId == id) selectedLineId = activeLines.keys.firstOrNull()
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
        if (config.visible == visible) return
        config.visible = visible
        if (visible) {
            if (!activeLines.containsKey(id)) createLineView(id, config)
        } else {
            activeLines[id]?.let {
                windowManager.removeView(it)
                activeLines.remove(id)
            }
        }
        saveState()
    }

    fun selectLine(id: String) {
        if (lineConfigs.containsKey(id)) selectedLineId = id
    }

    fun updateLineWidth(id: String, width: Int) {
        val config = lineConfigs[id] ?: return
        val view = activeLines[id] ?: return
        if (config.width != width) {
            config.width = width
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = width
            windowManager.updateViewLayout(view, params)
            saveState()
        }
    }

    fun moveSelectedLine(deltaX: Int) {
        val id = selectedLineId ?: return
        val view = activeLines[id] ?: return
        val config = lineConfigs[id] ?: return
        config.x += deltaX
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = config.x
        windowManager.updateViewLayout(view, params)
        saveState()
    }

    // --- THIS WAS THE MISSING FUNCTION ---
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
    // ------------------------------------

    // --- Private ---

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
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
                // Ensure this is ACCESSIBILITY_OVERLAY (Highest possible for 3rd party)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                
                // --- UPDATED FLAGS ---
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or // <--- ADD THIS
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                
                PixelFormat.OPAQUE
            )
            
        // Force the window to be "Text" type internally (sometimes helps with Z-ordering on older Androids)
        // params.setTitle("AmoledFixOverlay")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = config.x
            params.y = -100

            windowManager.addView(lineView, params)
            activeLines[id] = lineView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshAllLineLayouts() {
        val newHeight = getScreenHeight() + 200
        activeLines.forEach { (_, view) ->
            val params = view.layoutParams as WindowManager.LayoutParams
            if (params.height != newHeight) {
                params.height = newHeight
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun removeAllViews() {
        activeLines.values.forEach {
            try { windowManager.removeView(it) } catch (e: Exception) { }
        }
        activeLines.clear()
    }

    private fun saveState() {
        val prefs = getSharedPreferences("overlay_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            clear()
            putStringSet("line_ids", lineConfigs.keys)
            lineConfigs.forEach { (id, config) ->
                putInt("line_${id}_x", config.x)
                putInt("line_${id}_width", config.width)
                putBoolean("line_${id}_visible", config.visible)
            }
            apply()
        }
    }

    private fun restoreState() {
        val prefs = getSharedPreferences("overlay_prefs", MODE_PRIVATE)
        val ids = prefs.getStringSet("line_ids", emptySet()) ?: return
        ids.forEach { id ->
            val x = prefs.getInt("line_${id}_x", 0)
            val width = prefs.getInt("line_${id}_width", 5)
            val visible = prefs.getBoolean("line_${id}_visible", true)
            addLineInternal(id, x, width, visible)
        }
        if (activeLines.isNotEmpty()) selectedLineId = activeLines.keys.last()
    }
}
