package com.example.amoled_fix

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.amoled_fix/overlay"
    private var overlayService: OverlayService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as OverlayService.LocalBinder
            overlayService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            overlayService = null
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to OverlayService if it's running (or start and bind)
        // Since we want to interact with it, we should bind.
        val intent = Intent(this, OverlayService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startOverlay" -> {
                    if (Settings.canDrawOverlays(this)) {
                        startServiceAction(OverlayService.ACTION_START)
                        result.success("Started")
                    } else {
                        result.error("PERM_DENIED", "Overlay permission required", null)
                    }
                }
                "stopOverlay" -> {
                    startServiceAction(OverlayService.ACTION_STOP)
                    result.success("Stopped")
                }
                "addLine" -> {
                    startServiceAction(OverlayService.ACTION_ADD_LINE)
                    result.success("Line Added")
                }
                "updateWidth" -> {
                    val width = call.argument<Int>("width") ?: 5
                    val intent = Intent(this, OverlayService::class.java)
                    intent.action = OverlayService.ACTION_UPDATE_WIDTH
                    intent.putExtra("width", width)
                    startService(intent)
                    result.success(true)
                }
                "getLines" -> {
                    if (isBound && overlayService != null) {
                        result.success(overlayService?.getLineConfigs())
                    } else {
                        result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                    }
                }
                "setLines" -> {
                    if (isBound && overlayService != null) {
                        val lines = call.argument<List<Map<String, Int>>>("lines")
                        if (lines != null) {
                            overlayService?.setLineConfigs(lines)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Lines argument missing", null)
                        }
                    } else {
                         // Attempt to start and wait? For now just error.
                         // But if we want to restore profiles on app launch, service might not be running.
                         // If service is not running, we should start it.
                         if (Settings.canDrawOverlays(this)) {
                             startServiceAction(OverlayService.ACTION_START)
                             // Give it a moment to bind?
                             // Since binding is async, we can't immediately set lines.
                             // Ideally we would queue this or retry.
                             // For now, let's assume the user starts the overlay first.
                             result.error("SERVICE_NOT_READY", "Service not running or bound. Start overlay first.", null)
                         } else {
                             result.error("PERM_DENIED", "Overlay permission required", null)
                         }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun startServiceAction(action: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.action = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
