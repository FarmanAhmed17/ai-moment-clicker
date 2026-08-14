package com.example.moment_clicker

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private var detectorBridge: MomentDetectorBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        detectorBridge = MomentDetectorBridge(applicationContext).also {
            it.register(flutterEngine.dartExecutor.binaryMessenger)
        }
    }

    override fun onDestroy() {
        detectorBridge?.dispose()
        detectorBridge = null
        super.onDestroy()
    }
}
