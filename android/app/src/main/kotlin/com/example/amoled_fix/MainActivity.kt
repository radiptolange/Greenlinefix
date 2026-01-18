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
                     if (isBound && overlayService != null) {
                         val id = call.argument<String>("id")
                         val x = call.argument<Int>("x") ?: 0
                         val width = call.argument<Int>("width") ?: 5
                         val visible = call.argument<Boolean>("visible") ?: true
                         if (id != null) {
                             overlayService?.addLine(id, x, width, visible)
                             result.success(true)
                         } else {
                             result.error("INVALID_ARG", "Missing id", null)
                         }
                     } else {
                         startServiceAction(OverlayService.ACTION_START)
                         result.error("SERVICE_NOT_BOUND", "Service started, try again", null)
                     }
                }
                "removeLine" -> {
                    if (isBound && overlayService != null) {
                        val id = call.argument<String>("id")
                        if (id != null) {
                            overlayService?.removeLine(id)
                            result.success(true)
                        } else {
                             result.error("INVALID_ARG", "Missing id", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                    }
                }
                "toggleLine" -> {
                    if (isBound && overlayService != null) {
                        val id = call.argument<String>("id")
                        val visible = call.argument<Boolean>("visible")
                        if (id != null && visible != null) {
                            overlayService?.toggleLine(id, visible)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Missing id or visible", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                    }
                }
                "selectLine" -> {
                    if (isBound && overlayService != null) {
                        val id = call.argument<String>("id")
                        if (id != null) {
                            overlayService?.selectLine(id)
                            result.success(true)
                        } else {
                             result.error("INVALID_ARG", "Missing id", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                    }
                }
                "updateWidth" -> {
                     if (isBound && overlayService != null) {
                         val id = call.argument<String>("id")
                         val width = call.argument<Int>("width")
                         if (id != null && width != null) {
                             overlayService?.updateLineWidth(id, width)
                             result.success(true)
                         } else {
                             result.error("INVALID_ARG", "Missing args", null)
                         }
                     } else {
                          result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                     }
                }
                "getLines" -> {
                    if (isBound && overlayService != null) {
                        result.success(overlayService?.getLineConfigsMap())
                    } else {
                        result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                    }
                }
                "setLines" -> {
                    if (isBound && overlayService != null) {
                        val lines = call.argument<Map<String, Map<String, Any>>>("lines")
                        if (lines != null) {
                            overlayService?.setLineConfigsMap(lines)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Lines argument missing", null)
                        }
                    } else {
                         if (Settings.canDrawOverlays(this)) {
                             startServiceAction(OverlayService.ACTION_START)
                             result.error("SERVICE_NOT_READY", "Service not running or bound. Start overlay first.", null)
                         } else {
                             result.error("PERM_DENIED", "Overlay permission required", null)
                         }
                    }
                }
                "moveLine" -> {
                     if (isBound && overlayService != null) {
                         val delta = call.argument<Int>("delta")
                         if (delta != null) {
                             overlayService?.moveSelectedLine(delta)
                             result.success(true)
                         } else {
                             result.error("INVALID_ARG", "Missing delta", null)
                         }
                     } else {
                          result.error("SERVICE_NOT_BOUND", "Service not bound", null)
                     }
                }
                "resetLine" -> {
                     if (isBound && overlayService != null) {
                         overlayService?.resetSelectedLine()
                         result.success(true)
                     } else {
                          result.error("SERVICE_NOT_BOUND", "Service not bound", null)
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
