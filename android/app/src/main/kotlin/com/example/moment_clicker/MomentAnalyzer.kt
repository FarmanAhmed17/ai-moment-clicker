package com.example.moment_clicker

import com.google.mediapipe.framework.image.MPImage

/** Runs one MediaPipe task and turns its raw output into a moment detection. */
interface MomentAnalyzer {
    fun analyze(action: MomentAction, image: MPImage, timestampMs: Long): DetectionResult

    /** Drops any per frame history, e.g. when the watched moment changes. */
    fun reset()

    fun close()
}
