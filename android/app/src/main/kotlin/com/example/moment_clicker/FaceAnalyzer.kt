package com.example.moment_clicker

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.hypot

/**
 * Smile detection from the face landmarker. The smile blendshapes are the primary signal; the
 * mouth width to eye distance ratio is used as a fallback so a smile is still recognised when
 * blendshapes are unavailable.
 */
class FaceAnalyzer(context: Context, modelPath: String) : MomentAnalyzer {

    private companion object {
        const val SMILE_BLENDSHAPE_THRESHOLD = 0.45f
        const val SMILE_RATIO_THRESHOLD = 1.05f
        const val LEFT_EYE_OUTER = 33
        const val RIGHT_EYE_OUTER = 263
        const val MOUTH_LEFT = 61
        const val MOUTH_RIGHT = 291
    }

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )

    override fun analyze(action: MomentAction, image: MPImage, timestampMs: Long): DetectionResult {
        if (action != MomentAction.SMILE) return DetectionResult.none

        val result = landmarker.detectForVideo(image, timestampMs)
        val face = result.faceLandmarks().firstOrNull() ?: return DetectionResult.none

        val blendshapes = result.faceBlendshapes().orElse(emptyList()).firstOrNull()
        if (blendshapes != null) {
            val left = blendshapes.firstOrNull { it.categoryName() == "mouthSmileLeft" }?.score() ?: 0f
            val right = blendshapes.firstOrNull { it.categoryName() == "mouthSmileRight" }?.score() ?: 0f
            val score = (left + right) / 2f
            return DetectionResult(score >= SMILE_BLENDSHAPE_THRESHOLD, score)
        }

        val eyeSpan = hypot(
            face[LEFT_EYE_OUTER].x() - face[RIGHT_EYE_OUTER].x(),
            face[LEFT_EYE_OUTER].y() - face[RIGHT_EYE_OUTER].y(),
        )
        if (eyeSpan <= 0f) return DetectionResult.none
        val mouthSpan = hypot(
            face[MOUTH_LEFT].x() - face[MOUTH_RIGHT].x(),
            face[MOUTH_LEFT].y() - face[MOUTH_RIGHT].y(),
        )
        val ratio = mouthSpan / eyeSpan
        return DetectionResult(ratio >= SMILE_RATIO_THRESHOLD, (ratio / SMILE_RATIO_THRESHOLD).coerceIn(0f, 1f))
    }

    override fun reset() = Unit

    override fun close() = landmarker.close()
}
