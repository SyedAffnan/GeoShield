import 'failure.dart';

abstract final class FailureMessageMapper {
  // TODO: map failures to generated localized strings when user-facing flows are implemented.
  static String keyFor(Failure failure) => failure.code;
}
