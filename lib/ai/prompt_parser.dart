import 'moment_action.dart';

/// What a natural language prompt such as "take a picture when I wave"
/// resolved to.
class PromptInterpretation {
  const PromptInterpretation._(this.action, this.matches, this.message);

  /// The action the prompt asked for, or null when it could not be resolved
  /// confidently. Detection must not be started for a null action.
  final MomentAction? action;

  /// Every action the prompt mentioned; more than one means it was ambiguous.
  final Set<MomentAction> matches;

  /// User facing explanation, empty when the prompt resolved.
  final String message;

  bool get isResolved => action != null;
}

const Map<MomentAction, List<String>> _keywords = {
  MomentAction.thumbsUp: ['thumbs up', 'thumb up', 'thumbsup'],
  MomentAction.handsSpread: [
    'spread',
    'open hand',
    'open palm',
    'palms out',
    'hands out',
    'high five',
    'open my hand',
  ],
  MomentAction.handWave: ['wave', 'waving', 'waves'],
  MomentAction.smile: ['smile', 'smiling', 'smiles', 'grin', 'grinning'],
  MomentAction.jump: ['jump', 'jumping', 'jumps', 'leap', 'leaping'],
};

/// Maps free text onto one of the supported moments. Anything that matches no
/// moment, or more than one, is rejected rather than guessed at.
PromptInterpretation interpretPrompt(String prompt) {
  final normalized = prompt.toLowerCase().replaceAll(RegExp(r'[^a-z0-9 ]'), ' ');
  final padded = ' ${normalized.replaceAll(RegExp(r'\s+'), ' ').trim()} ';

  if (padded.trim().isEmpty) {
    return const PromptInterpretation._(
      null,
      {},
      'Describe the moment, for example "take a picture when I wave".',
    );
  }

  final matches = <MomentAction>{
    for (final entry in _keywords.entries)
      if (entry.value.any((keyword) => padded.contains(' $keyword'))) entry.key,
  };

  if (matches.isEmpty) {
    return PromptInterpretation._(
      null,
      matches,
      'Could not tell which moment to watch for. Try thumbs up, hands spread, '
      'wave, smile or jump, or pick one from the list.',
    );
  }
  if (matches.length > 1) {
    return PromptInterpretation._(
      null,
      matches,
      'That mentions ${matches.map((a) => a.label).join(' and ')}. '
      'Ask for a single moment.',
    );
  }
  return PromptInterpretation._(matches.single, matches, '');
}
