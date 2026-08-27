import '../../../core/network/api_client.dart';

class AdminStats {
  const AdminStats({
    required this.totalUsers,
    required this.touristCount,
    required this.responderCount,
    required this.adminCount,
    required this.totalIncidents,
    required this.activeIncidents,
    required this.resolvedIncidents,
    required this.totalLocationsRecorded,
    required this.activeSosAlerts,
  });

  factory AdminStats.fromJson(Map<String, dynamic> json) {
    return AdminStats(
      totalUsers: (json['totalUsers'] as num?)?.toInt() ?? 0,
      touristCount: (json['touristCount'] as num?)?.toInt() ?? 0,
      responderCount: (json['responderCount'] as num?)?.toInt() ?? 0,
      adminCount: (json['adminCount'] as num?)?.toInt() ?? 0,
      totalIncidents: (json['totalIncidents'] as num?)?.toInt() ?? 0,
      activeIncidents: (json['activeIncidents'] as num?)?.toInt() ?? 0,
      resolvedIncidents: (json['resolvedIncidents'] as num?)?.toInt() ?? 0,
      totalLocationsRecorded:
          (json['totalLocationsRecorded'] as num?)?.toInt() ?? 0,
      activeSosAlerts: (json['activeSosAlerts'] as num?)?.toInt() ?? 0,
    );
  }

  final int totalUsers;
  final int touristCount;
  final int responderCount;
  final int adminCount;
  final int totalIncidents;
  final int activeIncidents;
  final int resolvedIncidents;
  final int totalLocationsRecorded;
  final int activeSosAlerts;
}

class AdminUser {
  const AdminUser({
    required this.userId,
    required this.username,
    required this.email,
    required this.fullName,
    required this.phoneNumber,
    required this.role,
    required this.active,
    this.createdAt,
  });

  factory AdminUser.fromJson(Map<String, dynamic> json) {
    return AdminUser(
      userId: json['userId'] as String? ?? '',
      username: json['username'] as String? ?? '',
      email: json['email'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      phoneNumber: json['phoneNumber'] as String? ?? '',
      role: json['role'] as String? ?? '',
      active: json['active'] as bool? ?? true,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
    );
  }

  final String userId;
  final String username;
  final String email;
  final String fullName;
  final String phoneNumber;
  final String role;
  final bool active;
  final DateTime? createdAt;
}

class AdminIncident {
  const AdminIncident({
    required this.incidentId,
    required this.reporterId,
    required this.reporterUsername,
    required this.reporterFullName,
    required this.reporterPhoneNumber,
    required this.incidentType,
    required this.description,
    required this.latitude,
    required this.longitude,
    required this.status,
    this.createdAt,
  });

  factory AdminIncident.fromJson(Map<String, dynamic> json) {
    return AdminIncident(
      incidentId: json['incidentId'] as String? ?? '',
      reporterId: json['reporterId'] as String? ?? '',
      reporterUsername: json['reporterUsername'] as String? ?? '',
      reporterFullName: json['reporterFullName'] as String? ?? '',
      reporterPhoneNumber: json['reporterPhoneNumber'] as String? ?? '',
      incidentType: json['incidentType'] as String? ?? '',
      description: json['description'] as String? ?? '',
      latitude: (json['latitude'] as num?)?.toDouble() ?? 0.0,
      longitude: (json['longitude'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
    );
  }

  final String incidentId;
  final String reporterId;
  final String reporterUsername;
  final String reporterFullName;
  final String reporterPhoneNumber;
  final String incidentType;
  final String description;
  final double latitude;
  final double longitude;
  final String status;
  final DateTime? createdAt;
}

class AdminRepository {
  AdminRepository(this._client);
  final GeoShieldApiClient _client;

  Future<AdminStats> fetchStats() async {
    final data = await _client.getData('/api/v1/admin/stats');
    return AdminStats.fromJson(data as Map<String, dynamic>);
  }

  Future<List<AdminUser>> fetchUsers({String? role}) async {
    final queryParams = <String, dynamic>{};
    if (role != null && role.isNotEmpty) {
      queryParams['role'] = role;
    }
    final data = await _client.getData(
      '/api/v1/admin/users',
      queryParameters: queryParams.isNotEmpty ? queryParams : null,
    );
    final list = data as List<dynamic>? ?? <dynamic>[];
    return list
        .map((item) => AdminUser.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<AdminUser> provisionUser({
    required String username,
    required String email,
    required String password,
    required String fullName,
    required String phoneNumber,
    required String role,
  }) async {
    final data = await _client.postData(
      '/api/v1/admin/users/provision',
      data: {
        'username': username,
        'email': email,
        'password': password,
        'fullName': fullName,
        'phoneNumber': phoneNumber,
        'role': role,
      },
    );
    return AdminUser.fromJson(data);
  }

  Future<AdminUser> updateUserStatus({
    required String userId,
    required bool active,
  }) async {
    final data = await _client.patchData(
      '/api/v1/admin/users/$userId/status',
      data: {'active': active},
    );
    return AdminUser.fromJson(data);
  }

  Future<List<AdminIncident>> fetchIncidents() async {
    final data = await _client.getData('/api/v1/admin/incidents');
    final list = data as List<dynamic>? ?? <dynamic>[];
    return list
        .map((item) => AdminIncident.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
