package com.example.amoled_fix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val binder = LocalBinder()

    // Store reference to lines to manage them
    private val activeLines = mutableListOf<View>()
    private var activeLineWidth = 5 // Default px

    // The Floating Control Pad
    private var controlView: View? = null

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    companion object {
        const val CHANNEL_ID = "OverlayServiceChannel"
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_ADD_LINE = "ADD_LINE"
        const val ACTION_UPDATE_WIDTH = "UPDATE_WIDTH"
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Start Foreground to keep app alive
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
            // If we can't start foreground, we can't run.
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
            ACTION_ADD_LINE -> {
                addNewLine()
            }
            ACTION_UPDATE_WIDTH -> {
                val width = intent.getIntExtra("width", 5)
                activeLineWidth = width
                updateLinesWidth(width)
            }
        }
        return START_NOT_STICKY
    }

    fun getLineConfigs(): List<Map<String, Int>> {
        val configs = mutableListOf<Map<String, Int>>()
        activeLines.forEach { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            configs.add(mapOf("x" to params.x, "width" to params.width))
        }
        return configs
    }

    fun setLineConfigs(configs: List<Map<String, Int>>) {
        // Remove existing lines
        activeLines.forEach { windowManager.removeView(it) }
        activeLines.clear()

        // Add new lines
        configs.forEach { config ->
            val x = config["x"] ?: 0
            val width = config["width"] ?: 5
            addNewLine(x, width)
        }
    }

    // 1. Create the Black Line (Non-touchable)
    private fun addNewLine(x: Int = 0, width: Int = activeLineWidth) {
        try {
            val lineView = View(this)
            lineView.setBackgroundColor(Color.BLACK) // Pure black for OLED

            val params = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                // FLAG_NOT_FOCUSABLE: Lets keys go to app behind
                // FLAG_NOT_TOUCHABLE: Lets touch go to app behind
                // FLAG_LAYOUT_NO_LIMITS: Draws over status bar/nav bar
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            params.gravity = Gravity.CENTER // Start in center
            params.x = x

            windowManager.addView(lineView, params)
            activeLines.add(lineView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. Create Floating Controls (Touchable)
    private fun showControls() {
        try {
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent
            layout.setPadding(20, 20, 20, 20)

            // Button: Move Left
            val btnLeft = Button(this)
            btnLeft.text = "<"
            btnLeft.setOnClickListener { moveActiveLine(-5) }

            // Button: Move Right
            val btnRight = Button(this)
            btnRight.text = ">"
            btnRight.setOnClickListener { moveActiveLine(5) }

            // Button: Close
            val btnClose = Button(this)
            btnClose.text = "X"
            btnClose.setOnClickListener { stopSelf() }

            layout.addView(btnLeft)
            layout.addView(btnRight)
            layout.addView(btnClose)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                // FLAG_NOT_FOCUSABLE ensures keyboard works in other apps
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.y = 100 // Offset from bottom

            windowManager.addView(layout, params)
            controlView = layout
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Logic to move the *last added* line (simplified for UX)
    private fun moveActiveLine(deltaX: Int) {
        if (activeLines.isNotEmpty()) {
            val currentLine = activeLines.last()
            val params = currentLine.layoutParams as WindowManager.LayoutParams

            // Adjust X position based on Gravity logic
            // Because Gravity is CENTER, x is offset from center
            params.x += deltaX
            windowManager.updateViewLayout(currentLine, params)
        }
    }

    private fun updateLinesWidth(width: Int) {
        activeLines.forEach { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = width
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun removeAllViews() {
        if (controlView != null) {
            windowManager.removeView(controlView)
            controlView = null
        }
        activeLines.forEach { windowManager.removeView(it) }
        activeLines.clear()
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
