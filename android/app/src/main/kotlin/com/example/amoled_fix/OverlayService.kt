package com.example.amoled_fix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import java.util.UUID

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val binder = LocalBinder()

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

    // The Floating Control Pad
    private var controlView: View? = null

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    companion object {
        const val CHANNEL_ID = "OverlayServiceChannel"
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        try {
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val notification = builder
                .setContentTitle("AMOLED Fix Running")
                .setContentText("Overlay is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()

            startForeground(1, notification)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (controlView == null) showControls()
            }
            ACTION_STOP -> {
                removeAllViews()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // --- Public API for Binder ---

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
        removeAllLines()
        configs.forEach { (id, data) ->
            val x = (data["x"] as? Int) ?: 0
            val width = (data["width"] as? Int) ?: 5
            val visible = (data["visible"] as? Boolean) ?: true
            addLine(id, x, width, visible)
        }
        // Select last one by default if any
        if (activeLines.isNotEmpty()) {
            selectedLineId = activeLines.keys.last()
        }
    }

    fun addLine(id: String, x: Int = 0, width: Int = 5, visible: Boolean = true) {
        if (lineConfigs.containsKey(id)) return // Already exists

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
    }

    fun selectLine(id: String) {
        if (lineConfigs.containsKey(id)) {
            selectedLineId = id
        }
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

            // Calculate real screen height to ensure full coverage including nav bar/status bar
            val screenHeight = getScreenHeight() + 200 // Extra buffer

            val params = WindowManager.LayoutParams(
                config.width,
                screenHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.RGBA_8888
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            // Ensure absolute opacity
            params.alpha = 1.0f
            params.dimAmount = 0.0f

            // Use TOP | CENTER_HORIZONTAL to ensure full height from top
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = config.x
            // Offset negative y to ensure we cover top edge even if there are margins
            params.y = -100

            windowManager.addView(lineView, params)
            activeLines[id] = lineView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showControls() {
        try {
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.setBackgroundColor(Color.parseColor("#80000000"))
            layout.setPadding(20, 20, 20, 20)

            val btnLeft = Button(this)
            btnLeft.text = "<"
            btnLeft.setOnClickListener { moveSelectedLine(-5) }

            // Center Button (Reset to 0)
            val btnCenter = Button(this)
            btnCenter.text = "O"
            btnCenter.setOnClickListener { resetSelectedLine() }

            val btnRight = Button(this)
            btnRight.text = ">"
            btnRight.setOnClickListener { moveSelectedLine(5) }

            val btnClose = Button(this)
            btnClose.text = "X"
            btnClose.setOnClickListener { stopSelf() }

            layout.addView(btnLeft)
            layout.addView(btnCenter)
            layout.addView(btnRight)
            layout.addView(btnClose)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.y = 100

            windowManager.addView(layout, params)
            controlView = layout
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun moveSelectedLine(deltaX: Int) {
        val id = selectedLineId ?: return
        val view = activeLines[id] ?: return
        val config = lineConfigs[id] ?: return

        val params = view.layoutParams as WindowManager.LayoutParams
        config.x += deltaX
        params.x = config.x
        windowManager.updateViewLayout(view, params)
    }

    private fun resetSelectedLine() {
        val id = selectedLineId ?: return
        val view = activeLines[id] ?: return
        val config = lineConfigs[id] ?: return

        config.x = 0
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = 0
        windowManager.updateViewLayout(view, params)
    }

    private fun removeAllLines() {
        activeLines.values.forEach { windowManager.removeView(it) }
        activeLines.clear()
        lineConfigs.clear()
        selectedLineId = null
    }

    private fun removeAllViews() {
        if (controlView != null) {
            windowManager.removeView(controlView)
            controlView = null
        }
        removeAllLines()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllViews()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
