import 'package:flutter_test/flutter_test.dart';

import 'package:moment_clicker/ai/moment_action.dart';
import 'package:moment_clicker/ai/prompt_parser.dart';

void main() {
  group('interpretPrompt resolves natural phrasings', () {
    const cases = <String, MomentAction>{
      'Take a picture when I give a thumbs up': MomentAction.thumbsUp,
      'take a photo when i do a thumbs-up': MomentAction.thumbsUp,
      "Capture when I'm smiling": MomentAction.smile,
      'Capture when I smile': MomentAction.smile,
      'Take it when I wave my hand': MomentAction.handWave,
      'Take a photo when I wave': MomentAction.handWave,
      'Capture when I jump': MomentAction.jump,
      'take the shot when i am jumping': MomentAction.jump,
      'Take a photo when I spread my hands': MomentAction.handsSpread,
      'capture when I show an open palm': MomentAction.handsSpread,
    };

    cases.forEach((prompt, expected) {
      test(prompt, () {
        final interpretation = interpretPrompt(prompt);
        expect(interpretation.isResolved, isTrue);
        expect(interpretation.action, expected);
        expect(interpretation.message, isEmpty);
      });
    });
  });

  test('an empty prompt is not resolved', () {
    final interpretation = interpretPrompt('   ');
    expect(interpretation.action, isNull);
    expect(interpretation.message, isNotEmpty);
  });

  test('an unrelated prompt is not resolved', () {
    final interpretation = interpretPrompt('take a picture of the sunset');
    expect(interpretation.action, isNull);
    expect(interpretation.matches, isEmpty);
    expect(interpretation.message, contains('thumbs up'));
  });

  test('a prompt naming two moments is rejected as ambiguous', () {
    final interpretation = interpretPrompt('capture when I smile and jump');
    expect(interpretation.action, isNull);
    expect(interpretation.matches, {MomentAction.smile, MomentAction.jump});
  });

  test('thumbs down does not count as a thumbs up', () {
    expect(interpretPrompt('capture when I give a thumbs down').action, isNull);
  });
}
