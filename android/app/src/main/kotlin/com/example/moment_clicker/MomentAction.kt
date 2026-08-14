package com.example.moment_clicker

/** The moment the user asked the app to watch for. */
enum class MomentAction(val id: String, val detectionState: String) {
    THUMBS_UP("thumbsUp", "thumbsUpDetected"),
    HANDS_SPREAD("handsSpread", "handsSpreadDetected"),
    HAND_WAVE("handWave", "handWaveDetected"),
    SMILE("smile", "smileDetected"),
    JUMP("jump", "jumpDetected");

    companion object {
        fun fromId(id: String?): MomentAction? = entries.firstOrNull { it.id == id }
    }
}

/** Outcome of running one frame through an analyzer. */
data class DetectionResult(val detected: Boolean, val score: Float) {
    companion object {
        val none = DetectionResult(false, 0f)
    }
}
