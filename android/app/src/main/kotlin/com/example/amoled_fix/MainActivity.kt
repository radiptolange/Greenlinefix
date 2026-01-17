package com.example.amoled_fix

import android.content.Intent
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.amoled_fix/overlay"

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
