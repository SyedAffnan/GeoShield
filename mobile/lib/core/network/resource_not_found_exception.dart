/// Thrown when the server responds with HTTP 404 (Not Found).
///
/// Distinct from [NetworkException] (which signals transient connectivity
/// problems). A 404 is a definitive, non-transient server answer: the
/// requested resource does not exist.
class ResourceNotFoundException implements Exception {
  const ResourceNotFoundException([this.message = 'Resource not found.']);
  final String message;

  @override
  String toString() => message;
}
