import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:camera/camera.dart';
import 'dart:async';
import 'dart:io';
import 'package:gal/gal.dart';
import 'ai/moment_action.dart';
import 'ai/moment_detector.dart';
import 'ai/prompt_parser.dart';
late List<CameraDescription> cameras;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  cameras = await availableCameras();
  runApp(const MomentClicker());
}


class MomentClicker extends StatelessWidget {
  const MomentClicker({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Moment Clicker',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Moment Clicker"),
      ),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              "MOMENT CLICKER BY FARMAN",
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => CameraScreen(),
                  ),
                );
              },
              child: const Text("Capture Moment 📸"),
            ),
          ],
        ),
      ),
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  late CameraController _controller;
  late Future<void> _initializeControllerFuture;
  late final CameraDescription _camera;

  final TextEditingController _promptController = TextEditingController();
  late final MomentDetector _detector;
  StreamSubscription<MomentDetection>? _detectionSubscription;

  MomentAction _selectedAction = MomentAction.thumbsUp;
  MomentDetection? _lastDetection;
  String? _promptError;
  MomentAction? _promptPreview;
  bool _autoCaptureEnabled = false;
  bool _streaming = false;
  bool _capturing = false;

  @override
  void initState() {
    super.initState();

    _camera = cameras.firstWhere(
      (camera) => camera.lensDirection == CameraLensDirection.front,
      orElse: () => cameras[0],
    );

    _controller = CameraController(
      _camera,
      ResolutionPreset.medium,
      enableAudio: false,
    );

    _detector = MomentDetector(onMomentConfirmed: _onMomentConfirmed);
    _initializeControllerFuture = _controller.initialize();
  }

  @override
  void dispose() {
    _detectionSubscription?.cancel();
    _detector.dispose();
    _controller.dispose();
    _promptController.dispose();
    super.dispose();
  }

  Future<void> _toggleAutoCapture(bool enabled) async {
    if (enabled) {
      await _initializeControllerFuture;
      try {
        await _detector.start(_selectedAction);
      } on PlatformException catch (error) {
        // Detection is unavailable on this device (for example MediaPipe has no
        // native library for its ABI); stay in manual mode instead of pretending
        // the camera is watching.
        if (!mounted) return;
        setState(() {
          _autoCaptureEnabled = false;
          _promptError = "Detection unavailable: ${error.message}";
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Detection unavailable: ${error.message}")),
        );
        return;
      }
      _detectionSubscription ??= _detector.detections.listen((detection) {
        if (mounted) setState(() => _lastDetection = detection);
      });
      await _startStream();
    } else {
      await _stopStream();
      await _detector.stop();
    }
    if (!mounted) return;
    setState(() {
      _autoCaptureEnabled = enabled;
      _lastDetection = null;
    });
  }

  /// Live feedback while typing, so it is obvious whether the instruction is
  /// understood before it is submitted.
  void _previewPrompt(String prompt) {
    final preview = interpretPrompt(prompt).action;
    if (preview == _promptPreview && _promptError == null) return;
    setState(() {
      _promptPreview = preview;
      _promptError = null;
    });
  }

  /// Turns the typed instruction into the moment to watch for and arms auto
  /// capture. An instruction that does not map to exactly one moment leaves the
  /// detector untouched and only reports back to the user.
  Future<void> _applyPrompt(String prompt) async {
    final interpretation = interpretPrompt(prompt);
    if (!interpretation.isResolved) {
      setState(() => _promptError = interpretation.message);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(interpretation.message)),
      );
      return;
    }

    final action = interpretation.action!;
    setState(() {
      _promptError = null;
      _promptPreview = null;
      _selectedAction = action;
      _lastDetection = null;
    });

    if (_autoCaptureEnabled) {
      await _toggleAutoCapture(false);
    }
    await _toggleAutoCapture(true);
  }

  Future<void> _startStream() async {
    if (_streaming) return;
    _streaming = true;
    await _controller.startImageStream((image) {
      if (_capturing) return;
      _detector.processCameraImage(
        image,
        rotationDegrees: _camera.sensorOrientation,
        mirrored: _camera.lensDirection == CameraLensDirection.front,
      );
    });
  }

  Future<void> _stopStream() async {
    if (!_streaming) return;
    _streaming = false;
    await _controller.stopImageStream();
  }

  void _onMomentConfirmed(MomentDetection detection) {
    _capture(automatic: true);
  }

  Future<void> _capture({bool automatic = false}) async {
    if (_capturing) return;
    _capturing = true;
    try {
      // The image stream and takePicture cannot run at the same time, so the
      // stream pauses for the shot and resumes when the preview is dismissed.
      await _stopStream();
      final image = await _controller.takePicture();
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => PreviewScreen(image: image),
        ),
      );
    } finally {
      _capturing = false;
      if (mounted && _autoCaptureEnabled) {
        await _startStream();
      }
    }
  }

  /// The shutter doubles as the monitoring control: while a moment is being
  /// watched for only the detector may fire [_capture].
  String get _primaryButtonLabel {
    if (_autoCaptureEnabled) {
      return "WATCHING FOR ${_selectedAction.label} — TAP TO STOP";
    }
    return "CLICK MOMENT 📸";
  }

  Future<void> _onPrimaryButtonPressed() async {
    if (_autoCaptureEnabled) {
      await _toggleAutoCapture(false);
    } else if (_promptController.text.trim().isNotEmpty) {
      await _applyPrompt(_promptController.text);
    } else {
      await _capture();
    }
  }

  Widget _buildControls() {
    final detection = _lastDetection;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _promptController,
            textInputAction: TextInputAction.done,
            onSubmitted: _applyPrompt,
            onChanged: _previewPrompt,
            decoration: InputDecoration(
              hintText: "Take a picture when I give a thumbs up",
              border: const OutlineInputBorder(),
              errorText: _promptError,
              helperText: _promptPreview == null
                  ? null
                  : "Tap CLICK MOMENT to watch for ${_promptPreview!.label}",
              suffixIcon: IconButton(
                icon: const Icon(Icons.play_arrow),
                tooltip: "Watch for this moment",
                onPressed: () => _applyPrompt(_promptController.text),
              ),
            ),
          ),
          Row(
            children: [
              Expanded(
                child: DropdownButton<MomentAction>(
                  isExpanded: true,
                  value: _selectedAction,
                  items: [
                    for (final action in MomentAction.values)
                      DropdownMenuItem(value: action, child: Text(action.label)),
                  ],
                  onChanged: (action) async {
                    if (action == null) return;
                    setState(() {
                      _selectedAction = action;
                      _lastDetection = null;
                      _promptError = null;
                      _promptPreview = null;
                    });
                    if (_autoCaptureEnabled) await _detector.start(action);
                  },
                ),
              ),
              Switch(
                value: _autoCaptureEnabled,
                onChanged: (enabled) => _toggleAutoCapture(enabled),
              ),
              const Text("Auto"),
            ],
          ),
          if (_autoCaptureEnabled)
            Text(
              detection == null
                  ? "Watching for ${_selectedAction.label}..."
                  : "${detection.state}: ${detection.detected} "
                      "(${(detection.score * 100).toStringAsFixed(0)}%)",
              style: TextStyle(
                color: detection?.detected == true ? Colors.green : Colors.grey,
              ),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Camera"),
      ),
      body: FutureBuilder(
        future: _initializeControllerFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.done) {
            return Column(
              children: [
                _buildControls(),
                Expanded(
                  child: CameraPreview(_controller),
                ),
                ElevatedButton(
                  onPressed: _onPrimaryButtonPressed,
                  child: Text(_primaryButtonLabel),
                ),
              ],
            );
          } else {
            return const Center(
              child: CircularProgressIndicator(),
            );
          }
        },
      ),
    );
  }
}
class PreviewScreen extends StatelessWidget {
  final XFile image;

  const PreviewScreen({super.key, required this.image});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Preview"),
      ),
      body: SafeArea(
        child:  Column(
        children: [
          Expanded(
            child: Image.file(
              File(image.path),
              fit: BoxFit.contain,
            ),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              ElevatedButton(
                onPressed: () {
                  Navigator.pop(context);
                },
                child: const Text("Delete 🗑️"),
              ),
              ElevatedButton(
                onPressed: () async {
  await Gal.putImage(image.path);

  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text("Photo saved to gallery ✅"),
      ),
    );
    Navigator.pop(context);
  }
},
                child: const Text("Save ✅"),
              ),
            ],
          ),
          const SizedBox(height: 40),
        ],
      ),
    ),
  );
  }
}