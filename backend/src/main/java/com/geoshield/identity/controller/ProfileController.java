package com.geoshield.identity.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.identity.dto.DeactivateDeviceTokenRequest;
import com.geoshield.identity.dto.DeviceTokenRequest;
import com.geoshield.identity.dto.EmergencyContactRequest;
import com.geoshield.identity.dto.EmergencyContactResponse;
import com.geoshield.identity.dto.ProfileResponse;
import com.geoshield.identity.dto.UpdateProfileRequest;
import com.geoshield.identity.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@PreAuthorize("hasRole('TOURIST')")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<ProfileResponse> getProfile(Authentication authentication) {
        return ApiResponse.success("Profile retrieved", profileService.getProfile(currentUserId(authentication)));
    }

    @PutMapping
    public ApiResponse<ProfileResponse> updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success("Profile updated", profileService.updateProfile(currentUserId(authentication), request));
    }

    @GetMapping("/emergency-contacts")
    public ApiResponse<List<EmergencyContactResponse>> getEmergencyContacts(Authentication authentication) {
        return ApiResponse.success("Emergency contacts retrieved", profileService.getEmergencyContacts(currentUserId(authentication)));
    }

    @PostMapping("/emergency-contacts")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> createEmergencyContact(Authentication authentication,
            @Valid @RequestBody EmergencyContactRequest request) {
        EmergencyContactResponse response = profileService.createEmergencyContact(currentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Emergency contact created", response));
    }

    @PutMapping("/emergency-contacts/{contactId}")
    public ApiResponse<EmergencyContactResponse> updateEmergencyContact(Authentication authentication, @PathVariable Long contactId,
            @Valid @RequestBody EmergencyContactRequest request) {
        return ApiResponse.success("Emergency contact updated",
                profileService.updateEmergencyContact(currentUserId(authentication), contactId, request));
    }

    @DeleteMapping("/emergency-contacts/{contactId}")
    public ResponseEntity<Void> deleteEmergencyContact(Authentication authentication, @PathVariable Long contactId) {
        profileService.deleteEmergencyContact(currentUserId(authentication), contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/device-token")
    public ApiResponse<Void> registerDeviceToken(Authentication authentication, @Valid @RequestBody DeviceTokenRequest request) {
        profileService.registerDeviceToken(currentUserId(authentication), request);
        return ApiResponse.success("Device token registered", null);
    }

    @DeleteMapping("/device-token")
    public ResponseEntity<Void> deactivateDeviceToken(Authentication authentication,
            @Valid @RequestBody DeactivateDeviceTokenRequest request) {
        profileService.deactivateDeviceToken(currentUserId(authentication), request);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated principal does not contain a user identifier");
    }
}
