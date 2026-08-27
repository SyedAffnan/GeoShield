package com.geoshield.admin.service;

import com.geoshield.admin.dto.AdminIncidentSummaryResponse;
import com.geoshield.admin.dto.AdminStatsResponse;
import com.geoshield.identity.dto.ProvisionUserRequest;
import com.geoshield.identity.dto.UserSummaryResponse;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.incident.dto.ResponderIncidentResponse;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.service.LocationService;
import com.geoshield.sos.service.SosService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {
    private final IdentityService identityService;
    private final IncidentService incidentService;
    private final LocationService locationService;
    private final SosService sosService;

    public AdminServiceImpl(
            IdentityService identityService,
            IncidentService incidentService,
            LocationService locationService,
            SosService sosService) {
        this.identityService = identityService;
        this.incidentService = incidentService;
        this.locationService = locationService;
        this.sosService = sosService;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getSystemStats() {
        Map<Role, Long> userCounts = identityService.getUserCountsByRole();
        long tourists = userCounts.getOrDefault(Role.TOURIST, 0L);
        long responders = userCounts.getOrDefault(Role.RESPONDER, 0L);
        long admins = userCounts.getOrDefault(Role.ADMIN, 0L);
        long totalUsers = tourists + responders + admins;

        List<ResponderIncidentResponse> allIncidents = incidentService.getAllIncidentsForAdmin();
        long totalIncidents = allIncidents.size();
        long activeIncidents = allIncidents.stream()
                .filter(i -> !i.status().equalsIgnoreCase("RESOLVED") && !i.status().equalsIgnoreCase("CANCELLED"))
                .count();
        long resolvedIncidents = allIncidents.stream()
                .filter(i -> i.status().equalsIgnoreCase("RESOLVED"))
                .count();

        long totalLocations = locationService.getTotalLocationCount();
        long activeSos = sosService.getActiveSosCount();

        return new AdminStatsResponse(
                totalUsers,
                tourists,
                responders,
                admins,
                totalIncidents,
                activeIncidents,
                resolvedIncidents,
                totalLocations,
                activeSos
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getUsers(Role roleFilter) {
        return identityService.listUsers(roleFilter);
    }

    @Override
    @Transactional
    public UserSummaryResponse provisionUser(ProvisionUserRequest request) {
        return identityService.provisionUser(request);
    }

    @Override
    @Transactional
    public UserSummaryResponse updateUserStatus(UUID userId, boolean active) {
        return identityService.updateUserStatus(userId, active);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminIncidentSummaryResponse> getAllIncidents() {
        return incidentService.getAllIncidentsForAdmin().stream()
                .map(this::toAdminIncidentResponse)
                .toList();
    }

    private AdminIncidentSummaryResponse toAdminIncidentResponse(ResponderIncidentResponse incident) {
        return new AdminIncidentSummaryResponse(
                incident.incidentId(),
                incident.reporterId(),
                incident.reporterUsername(),
                incident.reporterFullName(),
                incident.reporterPhoneNumber(),
                incident.incidentType(),
                incident.description(),
                incident.latitude(),
                incident.longitude(),
                incident.status(),
                incident.sourceType(),
                incident.createdAt()
        );
    }
}
