import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'dart:io';
import 'package:gal/gal.dart';
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
        child: ElevatedButton(
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

  final TextEditingController _promptController = TextEditingController();

  @override
  void initState() {
    super.initState();

    _controller = CameraController(
      cameras[0],
      ResolutionPreset.medium,
    );

    _initializeControllerFuture = _controller.initialize();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
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
    TextField(
      controller: _promptController,
      decoration: const InputDecoration(
        hintText: "Describe the moment...",
        border: OutlineInputBorder(),
      ),
    ),
    Expanded(
      child: CameraPreview(_controller),
    ),ElevatedButton(
  onPressed: () async {
  final image = await _controller.takePicture();

  Navigator.push(
    context,
    MaterialPageRoute(
      builder: (context) => PreviewScreen(image: image),
    ),
  );
},
  child: const Text("CLICK MOMENT 📸"),
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