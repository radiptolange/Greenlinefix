package com.example.amoled_fix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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

    // Store reference to lines to manage them
    private val activeLines = mutableListOf<View>()
    private var activeLineWidth = 5 // Default px

    // The Floating Control Pad
    private var controlView: View? = null

    companion object {
        const val CHANNEL_ID = "OverlayServiceChannel"
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_ADD_LINE = "ADD_LINE"
        const val ACTION_UPDATE_WIDTH = "UPDATE_WIDTH"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Start Foreground to keep app alive
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AMOLED Fix Running")
            .setContentText("Overlay is active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (controlView == null) showControls()
            }
            ACTION_STOP -> {
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

    // 1. Create the Black Line (Non-touchable)
    private fun addNewLine() {
        val lineView = View(this)
        lineView.setBackgroundColor(Color.BLACK) // Pure black for OLED

        val params = WindowManager.LayoutParams(
            activeLineWidth,
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

        params.gravity = Gravity.CENTER // Start in center
        windowManager.addView(lineView, params)
        activeLines.add(lineView)
    }

    // 2. Create Floating Controls (Touchable)
    private fun showControls() {
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

    override fun onDestroy() {
        super.onDestroy()
        // Clean up all views
        if (controlView != null) windowManager.removeView(controlView)
        activeLines.forEach { windowManager.removeView(it) }
        activeLines.clear()
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
