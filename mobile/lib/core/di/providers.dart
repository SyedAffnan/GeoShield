import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Composition root for Riverpod dependencies.
/// TODO: register module repositories and data sources as their approved contracts are implemented.
final Provider<bool> skeletonProvider = Provider<bool>((Ref ref) => true);
