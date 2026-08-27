package com.geoshield.admin.dto;

public record AdminStatsResponse(
        long totalUsers,
        long touristCount,
        long responderCount,
        long adminCount,
        long totalIncidents,
        long activeIncidents,
        long resolvedIncidents,
        long totalLocationsRecorded,
        long activeSosAlerts
) { }
