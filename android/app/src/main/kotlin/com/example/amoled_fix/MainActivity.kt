package com.example.amoled_fix

import android.content.Intent
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
                "checkPermission" -> {
                    result.success(OverlayService.instance != null)
                }
                "requestPermission" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    result.success(true)
                }
                "startOverlay" -> {
                    if (OverlayService.instance != null) {
                        result.success("Started")
                    } else {
                        result.error("PERM_DENIED", "Enable Accessibility Service", null)
                    }
                }
                "stopOverlay" -> {
                    OverlayService.instance?.removeAllLines()
                    result.success("Stopped")
                }
                "addLine" -> {
                     val service = OverlayService.instance
                     if (service != null) {
                         val id = call.argument<String>("id")
                         val x = call.argument<Int>("x") ?: 0
                         val width = call.argument<Int>("width") ?: 5
                         val visible = call.argument<Boolean>("visible") ?: true
                         if (id != null) {
                             service.addLine(id, x, width, visible)
                             result.success(true)
                         } else {
                             result.error("INVALID_ARG", "Missing id", null)
                         }
                     } else {
                         result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                     }
                }
                "removeLine" -> {
                    val service = OverlayService.instance
                    if (service != null) {
                        val id = call.argument<String>("id")
                        if (id != null) {
                            service.removeLine(id)
                            result.success(true)
                        } else {
                             result.error("INVALID_ARG", "Missing id", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                    }
                }
                "toggleLine" -> {
                    val service = OverlayService.instance
                    if (service != null) {
                        val id = call.argument<String>("id")
                        val visible = call.argument<Boolean>("visible")
                        if (id != null && visible != null) {
                            service.toggleLine(id, visible)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Missing id or visible", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                    }
                }
                "selectLine" -> {
                    val service = OverlayService.instance
                    if (service != null) {
                        val id = call.argument<String>("id")
                        if (id != null) {
                            service.selectLine(id)
                            result.success(true)
                        } else {
                             result.error("INVALID_ARG", "Missing id", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                    }
                }
                "updateWidth" -> {
                     val service = OverlayService.instance
                     if (service != null) {
                         val id = call.argument<String>("id")
                         val width = call.argument<Int>("width")
                         if (id != null && width != null) {
                             service.updateLineWidth(id, width)
                             result.success(true)
                         } else {
                             result.error("INVALID_ARG", "Missing args", null)
                         }
                     } else {
                          result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                     }
                }
                "getLines" -> {
                    val service = OverlayService.instance
                    if (service != null) {
                        result.success(service.getLineConfigsMap())
                    } else {
                        result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                    }
                }
                "setLines" -> {
                    val service = OverlayService.instance
                    if (service != null) {
                        val lines = call.argument<Map<String, Map<String, Any>>>("lines")
                        if (lines != null) {
                            service.setLineConfigsMap(lines)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Lines argument missing", null)
                        }
                    } else {
                         result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                    }
                }
                "moveLine" -> {
                     val service = OverlayService.instance
                     if (service != null) {
                         val delta = call.argument<Int>("delta")
                         if (delta != null) {
                             service.moveSelectedLine(delta)
                             result.success(true)
                         } else {
                             result.error("INVALID_ARG", "Missing delta", null)
                         }
                     } else {
                          result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                     }
                }
                "resetLine" -> {
                     val service = OverlayService.instance
                     if (service != null) {
                         service.resetSelectedLine()
                         result.success(true)
                     } else {
                          result.error("SERVICE_NOT_RUNNING", "Enable Accessibility Service", null)
                     }
                }
                else -> result.notImplemented()
            }
        }
    }
}


