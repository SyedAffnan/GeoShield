import 'package:dio/dio.dart';

import '../storage/secure_session_storage.dart';
import 'auth_exception.dart';
import 'network_exception.dart';

abstract class ApiClient {
  Dio get dio;
}

/// HTTP client for the Spring Boot API. It never logs credentials or tokens.
class GeoShieldApiClient implements ApiClient {
  GeoShieldApiClient(this._sessionStorage, {Dio? dio})
      : _dio = dio ?? Dio(BaseOptions(baseUrl: _defaultBaseUrl));

  static const _defaultBaseUrl = String.fromEnvironment(
    'GEOSHIELD_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  final SecureSessionStorage _sessionStorage;
  final Dio _dio;

  @override
  Dio get dio {
    if (_dio.interceptors.whereType<_JwtInterceptor>().isEmpty) {
      _dio.interceptors.add(_JwtInterceptor(_sessionStorage));
    }
    return _dio;
  }

  Future<Map<String, dynamic>> getData(String path) async {
    try {
      final response = await dio.get<dynamic>(path);
      return _data(response.data);
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
      return _data(response.data);
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

  Map<String, dynamic> _data(dynamic responseBody) {
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
    return const NetworkException('Unable to reach the GeoShield service.');
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
