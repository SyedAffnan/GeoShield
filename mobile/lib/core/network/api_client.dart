import 'package:dio/dio.dart';

import '../storage/secure_session_storage.dart';
import 'auth_exception.dart';
import 'conflict_exception.dart';
import 'network_exception.dart';
import 'validation_exception.dart';

abstract class ApiClient {
  Dio get dio;
}

/// HTTP client for the Spring Boot API. It never logs credentials or tokens.
class GeoShieldApiClient implements ApiClient {
  /// Bounded timeouts to ensure mobile requests fail safely and reliably.
  static const Duration defaultConnectTimeout = Duration(seconds: 10);
  static const Duration defaultReceiveTimeout = Duration(seconds: 15);
  static const Duration defaultSendTimeout = Duration(seconds: 10);

  GeoShieldApiClient(this._sessionStorage, {Dio? dio})
      : _dio = dio ??
            Dio(
              BaseOptions(
                baseUrl: normalizeBaseUrl(defaultBaseUrl),
                connectTimeout: defaultConnectTimeout,
                receiveTimeout: defaultReceiveTimeout,
                sendTimeout: defaultSendTimeout,
              ),
            );

  /// Compile-time override retained for emulator, CI, and developer workflows.
  static const defaultBaseUrl = String.fromEnvironment(
    'GEOSHIELD_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  final SecureSessionStorage _sessionStorage;
  final Dio _dio;

  String get baseUrl => _dio.options.baseUrl;

  /// Changes only the host origin. Repository paths continue to own `/api/v1`.
  void setBaseUrl(String baseUrl) {
    _dio.options.baseUrl = normalizeBaseUrl(baseUrl);
  }

  /// Normalizes and validates the server base URL.
  /// Enforces HTTP/HTTPS scheme and valid host syntax. Non-local cleartext HTTP origins are rejected.
  static String normalizeBaseUrl(String value) {
    final uri = Uri.tryParse(value.trim());
    if (uri == null ||
        (uri.scheme != 'http' && uri.scheme != 'https') ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        (uri.path.isNotEmpty && uri.path != '/') ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const FormatException(
        'Enter a valid server URL such as https://api.geoshield.com or http://10.0.2.2:8080 without an API path.',
      );
    }
    if (uri.scheme == 'http' && !_isLocalDevelopmentHost(uri.host)) {
      throw const FormatException(
        'Production API endpoint must use HTTPS. Insecure HTTP is restricted to local development.',
      );
    }
    return uri.replace(path: '', query: null, fragment: null).toString();
  }

  static final RegExp _privateSubnet192 =
      RegExp(r'^192\.168\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$');

  static bool _isLocalDevelopmentHost(String host) {
    return host == 'localhost' ||
        host == '127.0.0.1' ||
        host == '10.0.2.2' ||
        host == '10.0.3.2' ||
        _privateSubnet192.hasMatch(host);
  }

  @override
  Dio get dio {
    if (_dio.interceptors.whereType<_JwtInterceptor>().isEmpty) {
      _dio.interceptors.add(_JwtInterceptor(_sessionStorage));
    }
    return _dio;
  }

  Future<dynamic> getData(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) async {
    try {
      final response = await dio.get<dynamic>(path, queryParameters: queryParameters);
      return _rawData(response.data);
    } on DioException catch (error) {
      throw _toException(error);
    }
  }

  Future<Map<String, dynamic>> postData(
    String path, {
    required Map<String, dynamic> data,
  }) async {
    try {
      final response = await dio.post<dynamic>(path, data: data);
      return _mapData(response.data);
    } on DioException catch (error) {
      throw _toException(error);
    }
  }

  Future<Map<String, dynamic>> patchData(
    String path, {
    required Map<String, dynamic> data,
  }) async {
    try {
      final response = await dio.patch<dynamic>(path, data: data);
      return _mapData(response.data);
    } on DioException catch (error) {
      throw _toException(error);
    }
  }

  Future<List<dynamic>> getListData(String path) async {
    try {
      final response = await dio.get<dynamic>(path);
      final body = response.data;
      if (body is Map<String, dynamic> && body['data'] is List) {
        return List<dynamic>.from(body['data'] as List);
      }
      throw const NetworkException('Unexpected server response.');
    } on DioException catch (error) {
      throw _toException(error);
    }
  }

  dynamic _rawData(dynamic responseBody) {
    if (responseBody is Map<String, dynamic>) {
      final data = responseBody['data'];
      if (data != null) return data;
    }
    throw const NetworkException('Unexpected server response.');
  }

  Map<String, dynamic> _mapData(dynamic responseBody) {
    if (responseBody is Map<String, dynamic> && responseBody['data'] is Map) {
      return Map<String, dynamic>.from(responseBody['data'] as Map);
    }
    throw const NetworkException('Unexpected server response.');
  }

  Exception _toException(DioException error) {
    final statusCode = error.response?.statusCode;
    if (statusCode == 401 || statusCode == 403) {
      return const AuthException(
          'Your session is no longer valid. Please sign in again.');
    }
    if (statusCode == 409) {
      return ConflictException(_serverMessage(error) ??
          'An account with those details already exists.');
    }
    if (statusCode == 400) {
      return ValidationException(
          _serverMessage(error) ?? 'The server rejected the submitted values.');
    }
    return const NetworkException('Unable to reach the GeoShield service.');
  }

  /// Reads the `message` field of the backend `ApiError` envelope so server-authored
  /// validation text can be shown. Request bodies are never read back or logged.
  String? _serverMessage(DioException error) {
    final body = error.response?.data;
    if (body is Map && body['message'] is String) {
      final message = body['message'] as String;
      return message.isEmpty ? null : message;
    }
    return null;
  }
}

class _JwtInterceptor extends Interceptor {
  _JwtInterceptor(this._sessionStorage);
  final SecureSessionStorage _sessionStorage;

  @override
  Future<void> onRequest(
      RequestOptions options, RequestInterceptorHandler handler) async {
    final token = await _sessionStorage.readAccessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }
}
