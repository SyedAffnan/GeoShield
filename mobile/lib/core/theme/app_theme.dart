import 'package:flutter/material.dart';

import '../design_system/app_colors.dart';
import '../design_system/risk_colors.dart';

abstract final class AppTheme {
  static ThemeData get light => _theme(Brightness.light);
  static ThemeData get dark => _theme(Brightness.dark);

  static ThemeData _theme(Brightness brightness) => ThemeData(
        useMaterial3: true,
        brightness: brightness,
        colorSchemeSeed: AppColors.primary,
        extensions: const <ThemeExtension<dynamic>>[RiskColors.standard()],
      );
}
