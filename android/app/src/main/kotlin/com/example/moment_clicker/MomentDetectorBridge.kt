package com.example.moment_clicker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import io.flutter.FlutterInjector
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridges the Flutter camera stream to MediaPipe: Flutter pushes frames over the method channel,
 * every frame is analysed on a background thread and the resulting detection state is pushed back
 * over the event channel.
 */
class MomentDetectorBridge(private val context: Context) : MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler {

    private companion object {
        const val TAG = "MomentDetector"
        const val METHOD_CHANNEL = "moment_clicker_ai"
        const val EVENT_CHANNEL = "moment_clicker_ai/detections"

        const val GESTURE_MODEL = "assets/models/gesture_recognizer.task"
        const val FACE_MODEL = "assets/models/face_landmarker.task"
        const val POSE_MODEL = "assets/models/pose_landmarker_lite.task"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    private var eventSink: EventChannel.EventSink? = null
    private var analyzer: MomentAnalyzer? = null
    private var action: MomentAction? = null
    private var lastTimestampMs = 0L

    fun register(messenger: BinaryMessenger) {
        MethodChannel(messenger, METHOD_CHANNEL).setMethodCallHandler(this)
        EventChannel(messenger, EVENT_CHANNEL).setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startDetection" -> {
                val requested = MomentAction.fromId(call.argument<String>("action"))
                if (requested == null) {
                    result.error("invalid_action", "Unknown action ${call.argument<String>("action")}", null)
                    return
                }
                try {
                    startDetection(requested)
                    result.success(true)
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to start detection", error)
                    result.error("start_failed", error.message, null)
                }
            }

            "stopDetection" -> {
                stopDetection()
                result.success(true)
            }

            "processFrame" -> {
                processFrame(call)
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    fun dispose() {
        stopDetection()
        executor.shutdown()
    }

    private fun startDetection(requested: MomentAction) {
        stopDetection()
        analyzer = when (requested) {
            MomentAction.THUMBS_UP, MomentAction.HANDS_SPREAD, MomentAction.HAND_WAVE ->
                GestureAnalyzer(context, assetPath(GESTURE_MODEL))

            MomentAction.SMILE -> FaceAnalyzer(context, assetPath(FACE_MODEL))
            MomentAction.JUMP -> PoseAnalyzer(context, assetPath(POSE_MODEL))
        }
        action = requested
        lastTimestampMs = 0L
    }

    private fun stopDetection() {
        val current = analyzer
        analyzer = null
        action = null
        executor.execute {
            current?.reset()
            current?.close()
        }
    }

    private fun processFrame(call: MethodCall) {
        val currentAction = action ?: return
        val currentAnalyzer = analyzer ?: return
        // Frames arrive faster than MediaPipe can consume them, so the newest frame is dropped
        // while an older one is still being analysed instead of building up a backlog.
        if (!busy.compareAndSet(false, true)) return

        val frame = CameraFrame(
            y = call.argument<ByteArray>("y") ?: return releaseBusy(),
            u = call.argument<ByteArray>("u") ?: return releaseBusy(),
            v = call.argument<ByteArray>("v") ?: return releaseBusy(),
            width = call.argument<Int>("width") ?: return releaseBusy(),
            height = call.argument<Int>("height") ?: return releaseBusy(),
            yRowStride = call.argument<Int>("yRowStride") ?: return releaseBusy(),
            uvRowStride = call.argument<Int>("uvRowStride") ?: return releaseBusy(),
            uvPixelStride = call.argument<Int>("uvPixelStride") ?: return releaseBusy(),
            rotationDegrees = call.argument<Int>("rotationDegrees") ?: 0,
            mirrored = call.argument<Boolean>("mirrored") ?: false,
        )

        executor.execute {
            try {
                val bitmap = FrameConverter.toBitmap(frame)
                if (bitmap != null) {
                    val timestampMs = nextTimestamp()
                    val image = BitmapImageBuilder(bitmap).build()
                    val detection = currentAnalyzer.analyze(currentAction, image, timestampMs)
                    bitmap.recycle()
                    emit(currentAction, detection)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Frame analysis failed", error)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun releaseBusy() {
        busy.set(false)
    }

    /** MediaPipe video mode requires strictly increasing timestamps. */
    @Synchronized
    private fun nextTimestamp(): Long {
        val now = System.currentTimeMillis()
        lastTimestampMs = if (now > lastTimestampMs) now else lastTimestampMs + 1
        return lastTimestampMs
    }

    private fun emit(action: MomentAction, detection: DetectionResult) {
        mainHandler.post {
            eventSink?.success(
                mapOf(
                    "action" to action.id,
                    "state" to action.detectionState,
                    "detected" to detection.detected,
                    "score" to detection.score.toDouble(),
                ),
            )
        }
    }

    private fun assetPath(asset: String): String =
        FlutterInjector.instance().flutterLoader().getLookupKeyForAsset(asset)
}
