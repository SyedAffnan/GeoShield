/// A request the server rejected with a field-level validation message.
class ValidationException implements Exception {
  const ValidationException(this.message);

  /// The message authored by the backend's `ApiError` envelope.
  final String message;
}
