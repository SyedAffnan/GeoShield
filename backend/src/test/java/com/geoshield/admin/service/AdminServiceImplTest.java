package com.geoshield.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.admin.dto.AdminStatsResponse;
import com.geoshield.identity.dto.ProvisionUserRequest;
import com.geoshield.identity.dto.UserSummaryResponse;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.incident.dto.ResponderIncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.service.LocationService;
import com.geoshield.sos.service.SosService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {
    @Mock private IdentityService identityService;
    @Mock private IncidentService incidentService;
    @Mock private LocationService locationService;
    @Mock private SosService sosService;

    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(identityService, incidentService, locationService, sosService);
    }

    @Test
    void getSystemStatsCalculatesAggregatesCorrectly() {
        when(identityService.getUserCountsByRole()).thenReturn(Map.of(
                Role.TOURIST, 10L,
                Role.RESPONDER, 3L,
                Role.ADMIN, 1L
        ));

        ResponderIncidentResponse inc1 = new ResponderIncidentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "tourist1", "Tourist One", "+919876543210",
                "THEFT", "Bag stolen", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                "REPORTED", IncidentSourceType.USER_REPORTED, Instant.now()
        );
        ResponderIncidentResponse inc2 = new ResponderIncidentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "tourist2", "Tourist Two", "+919876543211",
                "ACCIDENT", "Bike crash", new BigDecimal("12.9717"), new BigDecimal("77.5947"),
                "RESOLVED", IncidentSourceType.USER_REPORTED, Instant.now()
        );

        when(incidentService.getAllIncidentsForAdmin()).thenReturn(List.of(inc1, inc2));
        when(locationService.getTotalLocationCount()).thenReturn(45L);
        when(sosService.getActiveSosCount()).thenReturn(2L);

        AdminStatsResponse stats = adminService.getSystemStats();

        assertThat(stats.totalUsers()).isEqualTo(14L);
        assertThat(stats.touristCount()).isEqualTo(10L);
        assertThat(stats.responderCount()).isEqualTo(3L);
        assertThat(stats.adminCount()).isEqualTo(1L);
        assertThat(stats.totalIncidents()).isEqualTo(2L);
        assertThat(stats.activeIncidents()).isEqualTo(1L);
        assertThat(stats.resolvedIncidents()).isEqualTo(1L);
        assertThat(stats.totalLocationsRecorded()).isEqualTo(45L);
        assertThat(stats.activeSosAlerts()).isEqualTo(2L);
    }

    @Test
    void provisionUserDelegatesToIdentityService() {
        ProvisionUserRequest request = new ProvisionUserRequest(
                "responder1", "resp1@example.com", "SecurePass1!", "Responder One", "+919876543212", Role.RESPONDER
        );
        UserSummaryResponse response = new UserSummaryResponse(
                UUID.randomUUID(), request.username(), request.email(), request.fullName(),
                request.phoneNumber(), Role.RESPONDER, true, Instant.now()
        );
        when(identityService.provisionUser(request)).thenReturn(response);

        UserSummaryResponse result = adminService.provisionUser(request);

        assertThat(result).isEqualTo(response);
        verify(identityService).provisionUser(request);
    }

    @Test
    void updateUserStatusDelegatesToIdentityService() {
        UUID userId = UUID.randomUUID();
        UserSummaryResponse response = new UserSummaryResponse(
                userId, "user1", "user1@example.com", "User One", "+919876543210", Role.TOURIST, false, Instant.now()
        );
        when(identityService.updateUserStatus(userId, false)).thenReturn(response);

        UserSummaryResponse result = adminService.updateUserStatus(userId, false);

        assertThat(result.active()).isFalse();
        verify(identityService).updateUserStatus(userId, false);
    }
}
