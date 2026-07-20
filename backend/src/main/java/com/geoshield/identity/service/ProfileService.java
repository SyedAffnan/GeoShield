package com.geoshield.identity.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.identity.dto.DeactivateDeviceTokenRequest;
import com.geoshield.identity.dto.DeviceTokenRequest;
import com.geoshield.identity.dto.EmergencyContactRequest;
import com.geoshield.identity.dto.EmergencyContactResponse;
import com.geoshield.identity.dto.ProfileResponse;
import com.geoshield.identity.dto.UpdateProfileRequest;
import java.util.List;
import java.util.UUID;

public interface ProfileService extends ModuleService {
    ProfileResponse getProfile(UUID userId);
    ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);
    List<EmergencyContactResponse> getEmergencyContacts(UUID userId);
    EmergencyContactResponse createEmergencyContact(UUID userId, EmergencyContactRequest request);
    EmergencyContactResponse updateEmergencyContact(UUID userId, Long contactId, EmergencyContactRequest request);
    void deleteEmergencyContact(UUID userId, Long contactId);
    void registerDeviceToken(UUID userId, DeviceTokenRequest request);
    void deactivateDeviceToken(UUID userId, DeactivateDeviceTokenRequest request);
}
