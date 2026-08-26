/// A request the server rejected because it duplicates an existing record.
class ConflictException implements Exception {
  const ConflictException(this.message);

  /// The message authored by the backend's `ApiError` envelope.
  final String message;
}
