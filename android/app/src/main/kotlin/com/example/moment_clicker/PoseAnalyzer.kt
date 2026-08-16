package com.example.moment_clicker

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * Jump detection from the pose landmarker. A jump is a hip rise well above the standing baseline
 * that happens fast enough to not be confused with the person simply standing up straighter, and
 * it is only reported while both feet have left their resting height.
 */
class PoseAnalyzer(context: Context, modelPath: String) : MomentAnalyzer {

    private companion object {
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28

        const val HISTORY_MS = 2000L
        const val BASELINE_MIN_SAMPLES = 8
        const val RISE_THRESHOLD = 0.045f
        const val VELOCITY_THRESHOLD = 0.12f
        const val MIN_VISIBILITY = 0.5f
    }

    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )

    private data class Sample(val timestampMs: Long, val hipY: Float, val ankleY: Float)

    private val history = ArrayDeque<Sample>()

    override fun analyze(action: MomentAction, image: MPImage, timestampMs: Long): DetectionResult {
        if (action != MomentAction.JUMP) return DetectionResult.none

        val pose = landmarker.detectForVideo(image, timestampMs).landmarks().firstOrNull()
        if (pose == null || pose.size <= RIGHT_ANKLE) {
            history.clear()
            return DetectionResult.none
        }

        val hips = listOf(pose[LEFT_HIP], pose[RIGHT_HIP])
        if (hips.any { it.visibility().orElse(1f) < MIN_VISIBILITY }) return DetectionResult.none

        val hipY = hips.map { it.y() }.average().toFloat()
        val ankleY = listOf(pose[LEFT_ANKLE], pose[RIGHT_ANKLE]).map { it.y() }.average().toFloat()

        history.addLast(Sample(timestampMs, hipY, ankleY))
        while (history.size > 2 && timestampMs - history.first().timestampMs > HISTORY_MS) {
            history.removeFirst()
        }
        if (history.size < BASELINE_MIN_SAMPLES) return DetectionResult(false, 0f)

        // The standing height is the median of the window, which stays stable while the short
        // airborne part of the jump only affects a few samples.
        val baselineHip = history.map { it.hipY }.sorted()[history.size / 2]
        val baselineAnkle = history.map { it.ankleY }.sorted()[history.size / 2]

        val rise = baselineHip - hipY
        val feetRise = baselineAnkle - ankleY
        val previous = history.elementAt(history.size - 2)
        val elapsedSeconds = ((timestampMs - previous.timestampMs).coerceAtLeast(1L)) / 1000f
        val upwardVelocity = (previous.hipY - hipY) / elapsedSeconds

        val detected = rise >= RISE_THRESHOLD &&
            feetRise >= RISE_THRESHOLD / 2f &&
            upwardVelocity >= VELOCITY_THRESHOLD
        val score = (rise / (RISE_THRESHOLD * 2f)).coerceIn(0f, 1f)
        return DetectionResult(detected, score)
    }

    override fun reset() = history.clear()

    override fun close() = landmarker.close()
}
