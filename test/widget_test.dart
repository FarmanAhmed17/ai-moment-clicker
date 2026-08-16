import 'package:flutter_test/flutter_test.dart';

import 'package:moment_clicker/main.dart';

void main() {
  testWidgets('home page offers the capture entry point', (WidgetTester tester) async {
    await tester.pumpWidget(const MomentClicker());

    expect(find.text('Moment Clicker'), findsOneWidget);
    expect(find.text('Capture Moment 📸'), findsOneWidget);
  });
}
