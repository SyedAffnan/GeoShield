import 'package:flutter/material.dart';

class RiskColors extends ThemeExtension<RiskColors> {
  const RiskColors({required this.low, required this.medium, required this.high, required this.critical});
  const RiskColors.standard() : low = Colors.green, medium = Colors.amber, high = Colors.orange, critical = Colors.red;

  final Color low;
  final Color medium;
  final Color high;
  final Color critical;

  @override RiskColors copyWith({Color? low, Color? medium, Color? high, Color? critical}) => RiskColors(
    low: low ?? this.low, medium: medium ?? this.medium, high: high ?? this.high, critical: critical ?? this.critical,
  );

  @override RiskColors lerp(ThemeExtension<RiskColors>? other, double t) => other is! RiskColors ? this : RiskColors(
    low: Color.lerp(low, other.low, t)!, medium: Color.lerp(medium, other.medium, t)!,
    high: Color.lerp(high, other.high, t)!, critical: Color.lerp(critical, other.critical, t)!,
  );
}
