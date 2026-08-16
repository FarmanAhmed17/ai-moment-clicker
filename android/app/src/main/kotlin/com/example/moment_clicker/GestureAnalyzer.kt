package com.example.moment_clicker

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Hand based moments. The built in classifier is only used as one signal for thumbs up; the
 * remaining moments are derived from the 21 hand landmarks, and the wave additionally needs the
 * wrist trajectory of the last couple of seconds.
 */
class GestureAnalyzer(context: Context, modelPath: String) : MomentAnalyzer {

    private companion object {
        const val WRIST = 0
        const val THUMB_MCP = 2
        const val THUMB_IP = 3
        const val THUMB_TIP = 4
        const val INDEX_MCP = 5
        const val INDEX_PIP = 6
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        const val MIDDLE_PIP = 10
        const val MIDDLE_TIP = 12
        const val RING_PIP = 14
        const val RING_TIP = 16
        const val PINKY_MCP = 17
        const val PINKY_PIP = 18
        const val PINKY_TIP = 20

        const val THUMB_UP_CLASSIFIER_SCORE = 0.55f
        const val SPREAD_RATIO = 1.55f
        const val WAVE_WINDOW_MS = 1600L
        const val WAVE_MIN_AMPLITUDE = 0.07f
        const val WAVE_MIN_REVERSALS = 2
    }

    private val recognizer: GestureRecognizer = GestureRecognizer.createFromOptions(
        context,
        GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )

    private val wristTrack = ArrayDeque<Pair<Long, Float>>()

    override fun analyze(action: MomentAction, image: MPImage, timestampMs: Long): DetectionResult {
        val result = recognizer.recognizeForVideo(image, timestampMs)
        return when (action) {
            MomentAction.THUMBS_UP -> thumbsUp(result)
            MomentAction.HANDS_SPREAD -> handsSpread(result)
            MomentAction.HAND_WAVE -> handWave(result, timestampMs)
            else -> DetectionResult.none
        }
    }

    override fun reset() = wristTrack.clear()

    override fun close() = recognizer.close()

    private fun thumbsUp(result: GestureRecognizerResult): DetectionResult {
        val hands = result.landmarks()
        if (hands.isEmpty()) return DetectionResult.none

        val classifierScore = result.gestures()
            .flatten()
            .filter { it.categoryName() == "Thumb_Up" }
            .maxOfOrNull { it.score() } ?: 0f

        val landmarkScore = hands.maxOf { thumbsUpFromLandmarks(it) }
        val score = maxOf(classifierScore, landmarkScore)
        // The classifier alone is happy with a half curled fist, so a strong classifier hit still
        // has to be backed by a plausible hand shape.
        val detected = landmarkScore > 0.5f || (classifierScore >= THUMB_UP_CLASSIFIER_SCORE && landmarkScore > 0.3f)
        return DetectionResult(detected, score)
    }

    /** Thumb pointing up while the other four fingers stay curled into the palm. */
    private fun thumbsUpFromLandmarks(hand: List<NormalizedLandmark>): Float {
        val palm = palmSize(hand)
        if (palm <= 0f) return 0f

        // Image coordinates grow downwards, so "up" means a smaller y.
        val thumbRise = (hand[THUMB_MCP].y() - hand[THUMB_TIP].y()) / palm
        val thumbStraight = hand[THUMB_TIP].y() < hand[THUMB_IP].y()
        val thumbVertical = abs(hand[THUMB_TIP].x() - hand[THUMB_MCP].x()) / palm < 0.9f

        val curled = listOf(
            INDEX_TIP to INDEX_PIP,
            MIDDLE_TIP to MIDDLE_PIP,
            RING_TIP to RING_PIP,
            PINKY_TIP to PINKY_PIP,
        ).count { (tip, pip) -> distance(hand[tip], hand[WRIST]) < distance(hand[pip], hand[WRIST]) * 1.15f }

        if (!thumbStraight || !thumbVertical || curled < 3) return 0f
        val rise = ((thumbRise - 0.3f) / 0.5f).coerceIn(0f, 1f)
        return (0.55f + 0.45f * rise) * (curled / 4f)
    }

    /** Open palm with every finger extended and the fingertips fanned out. */
    private fun handsSpread(result: GestureRecognizerResult): DetectionResult {
        val hands = result.landmarks()
        if (hands.isEmpty()) return DetectionResult.none

        val score = hands.maxOf { hand ->
            val palm = palmSize(hand)
            if (palm <= 0f) return@maxOf 0f

            val extended = listOf(
                INDEX_TIP to INDEX_PIP,
                MIDDLE_TIP to MIDDLE_PIP,
                RING_TIP to RING_PIP,
                PINKY_TIP to PINKY_PIP,
            ).count { (tip, pip) -> distance(hand[tip], hand[WRIST]) > distance(hand[pip], hand[WRIST]) * 1.25f }
            if (extended < 4) return@maxOf 0f

            val thumbOut = distance(hand[THUMB_TIP], hand[INDEX_MCP]) / palm
            val fan = (distance(hand[INDEX_TIP], hand[MIDDLE_TIP]) +
                distance(hand[MIDDLE_TIP], hand[RING_TIP]) +
                distance(hand[RING_TIP], hand[PINKY_TIP])) / palm
            if (fan < SPREAD_RATIO || thumbOut < 0.75f) return@maxOf 0f

            ((fan - SPREAD_RATIO) / 1.2f).coerceIn(0f, 1f) * 0.4f + 0.6f
        }
        return DetectionResult(score > 0.5f, score)
    }

    /**
     * A wave is horizontal wrist travel that changes direction at least twice inside the tracking
     * window, so a hand that is merely held up or moved once across the frame is not enough.
     */
    private fun handWave(result: GestureRecognizerResult, timestampMs: Long): DetectionResult {
        val hand = result.landmarks().firstOrNull()
        if (hand == null) {
            wristTrack.clear()
            return DetectionResult.none
        }

        wristTrack.addLast(timestampMs to hand[WRIST].x())
        while (wristTrack.size > 2 && timestampMs - wristTrack.first().first > WAVE_WINDOW_MS) {
            wristTrack.removeFirst()
        }
        if (wristTrack.size < 6) return DetectionResult(false, 0f)

        val xs = wristTrack.map { it.second }
        val amplitude = (xs.max() - xs.min())
        if (amplitude < WAVE_MIN_AMPLITUDE) return DetectionResult(false, 0f)

        var reversals = 0
        var direction = 0
        for (i in 1 until xs.size) {
            val delta = xs[i] - xs[i - 1]
            if (abs(delta) < WAVE_MIN_AMPLITUDE / 6f) continue
            val step = if (delta > 0) 1 else -1
            if (direction != 0 && step != direction) reversals++
            direction = step
        }

        val score = (amplitude / (WAVE_MIN_AMPLITUDE * 3f)).coerceIn(0f, 1f)
        return DetectionResult(reversals >= WAVE_MIN_REVERSALS, score)
    }

    private fun palmSize(hand: List<NormalizedLandmark>): Float =
        maxOf(
            distance(hand[WRIST], hand[MIDDLE_MCP]),
            distance(hand[INDEX_MCP], hand[PINKY_MCP]),
        )

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())
}
