import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/services.dart';

import 'moment_action.dart';

/// One detection state coming back from the native MediaPipe pipeline,
/// e.g. `thumbsUpDetected` with the confidence that produced it.
class MomentDetection {
  const MomentDetection({
    required this.action,
    required this.state,
    required this.detected,
    required this.score,
  });

  factory MomentDetection.fromMap(Map<Object?, Object?> map) {
    final id = map['action'] as String?;
    return MomentDetection(
      action: MomentAction.values.firstWhere((a) => a.id == id),
      state: map['state'] as String? ?? '',
      detected: map['detected'] as bool? ?? false,
      score: (map['score'] as num? ?? 0).toDouble(),
    );
  }

  final MomentAction action;
  final String state;
  final bool detected;
  final double score;
}

/// Streams camera frames to the native MediaPipe analyzers and decides when a
/// detection is trustworthy enough to fire the shutter.
///
/// A capture needs [_requiredConsecutiveFrames] confirmations in a row; after a
/// capture the detector waits out [_cooldown] and additionally requires the
/// moment to disappear for [_requiredClearFrames] frames, so holding a thumbs up
/// in front of the lens cannot machine gun the camera.
class MomentDetector {
  MomentDetector({this.onMomentConfirmed});

  static const MethodChannel _methodChannel = MethodChannel('moment_clicker_ai');
  static const EventChannel _eventChannel = EventChannel('moment_clicker_ai/detections');

  static const Duration _cooldown = Duration(seconds: 3);
  static const Duration _minFrameInterval = Duration(milliseconds: 80);
  static const int _requiredConsecutiveFrames = 3;
  static const int _requiredClearFrames = 5;
  static const double _minScore = 0.5;

  final void Function(MomentDetection detection)? onMomentConfirmed;

  final StreamController<MomentDetection> _detections =
      StreamController<MomentDetection>.broadcast();

  StreamSubscription<dynamic>? _nativeSubscription;
  MomentAction? _action;
  DateTime _lastFrameSentAt = DateTime.fromMillisecondsSinceEpoch(0);
  DateTime? _lastCaptureAt;
  int _consecutiveHits = 0;
  int _clearFrames = _requiredClearFrames;
  bool _armed = true;

  /// Latest detection state for the watched moment, for UI feedback.
  Stream<MomentDetection> get detections => _detections.stream;

  MomentAction? get action => _action;

  Future<void> start(MomentAction action) async {
    await stop();
    _action = action;
    _resetGating();
    _nativeSubscription = _eventChannel.receiveBroadcastStream().listen(_onNativeEvent);
    await _methodChannel.invokeMethod<bool>('startDetection', {'action': action.id});
  }

  Future<void> stop() async {
    _action = null;
    await _nativeSubscription?.cancel();
    _nativeSubscription = null;
    await _methodChannel.invokeMethod<bool>('stopDetection');
  }

  Future<void> dispose() async {
    await stop();
    await _detections.close();
  }

  /// Forwards a camera frame to the native side, throttled so MediaPipe is not
  /// flooded with frames it cannot keep up with.
  Future<void> processCameraImage(
    CameraImage image, {
    required int rotationDegrees,
    required bool mirrored,
  }) async {
    if (_action == null || image.planes.length < 3) return;

    final now = DateTime.now();
    if (now.difference(_lastFrameSentAt) < _minFrameInterval) return;
    _lastFrameSentAt = now;

    await _methodChannel.invokeMethod<void>('processFrame', {
      'y': image.planes[0].bytes,
      'u': image.planes[1].bytes,
      'v': image.planes[2].bytes,
      'width': image.width,
      'height': image.height,
      'yRowStride': image.planes[0].bytesPerRow,
      'uvRowStride': image.planes[1].bytesPerRow,
      'uvPixelStride': image.planes[1].bytesPerPixel ?? 1,
      'rotationDegrees': rotationDegrees,
      'mirrored': mirrored,
    });
  }

  void _onNativeEvent(dynamic event) {
    if (event is! Map) return;
    final detection = MomentDetection.fromMap(event.cast<Object?, Object?>());
    if (detection.action != _action) return;

    _detections.add(detection);
    _evaluate(detection);
  }

  void _evaluate(MomentDetection detection) {
    if (!detection.detected || detection.score < _minScore) {
      _consecutiveHits = 0;
      _clearFrames++;
      if (_clearFrames >= _requiredClearFrames) _armed = true;
      return;
    }

    _clearFrames = 0;
    if (!_armed) return;

    final lastCapture = _lastCaptureAt;
    if (lastCapture != null && DateTime.now().difference(lastCapture) < _cooldown) return;

    _consecutiveHits++;
    if (_consecutiveHits < _requiredConsecutiveFrames) return;

    _consecutiveHits = 0;
    _armed = false;
    _lastCaptureAt = DateTime.now();
    onMomentConfirmed?.call(detection);
  }

  void _resetGating() {
    _consecutiveHits = 0;
    _clearFrames = _requiredClearFrames;
    _armed = true;
    _lastCaptureAt = null;
  }
}
