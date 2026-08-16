/// The moments the app can watch for through the camera.
enum MomentAction {
  thumbsUp('thumbsUp', 'Thumbs Up 👍', 'thumbsUpDetected'),
  handsSpread('handsSpread', 'Hands Spread 🖐️', 'handsSpreadDetected'),
  handWave('handWave', 'Hand Wave 👋', 'handWaveDetected'),
  smile('smile', 'Smile 😄', 'smileDetected'),
  jump('jump', 'Jump 🤸', 'jumpDetected');

  const MomentAction(this.id, this.label, this.detectionState);

  final String id;
  final String label;
  final String detectionState;
}
