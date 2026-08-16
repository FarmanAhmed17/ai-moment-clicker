# moment_clicker

Flutter camera app that clicks the shutter by itself when it sees the moment you picked:
thumbs up, hands spread, hand wave, smile or jump.

Type an instruction such as "take a picture when I give a thumbs up" and submit it, or pick the
moment from the dropdown. A prompt that does not name exactly one supported moment is reported
back and leaves detection off.

## How auto capture works

The camera screen streams frames over the `moment_clicker_ai` method channel to the Android side,
which runs them through MediaPipe Tasks Vision and pushes detection states
(`thumbsUpDetected`, `handsSpreadDetected`, `handWaveDetected`, `smileDetected`, `jumpDetected`)
back over the `moment_clicker_ai/detections` event channel.

| Moment | Model | Signal |
| --- | --- | --- |
| Thumbs up | `gesture_recognizer.task` | `Thumb_Up` category confirmed by thumb/finger landmark geometry |
| Hands spread | `gesture_recognizer.task` | all fingers extended and fingertips fanned out relative to palm size |
| Hand wave | `gesture_recognizer.task` | wrist direction reversals within a 1.6 s window |
| Smile | `face_landmarker.task` | `mouthSmileLeft` / `mouthSmileRight` blendshapes |
| Jump | `pose_landmarker_lite.task` | hip rise and upward velocity against the standing baseline |

Captures are gated by a confidence threshold, three consecutive confirmations, a three second
cooldown, and a re-arm that needs the moment to disappear first.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
