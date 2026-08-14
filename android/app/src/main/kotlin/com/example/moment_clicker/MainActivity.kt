package com.example.moment_clicker

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "moment_clicker_ai"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MethodChannel(
            flutterEngine!!.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                "startGestureDetection" -> {
                    // yaha gesture recognizer start hoga
                    result.success("Gesture detection started")
                }

                "startFaceDetection" -> {
                    // yaha face landmarker start hoga
                    result.success("Face detection started")
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }
}