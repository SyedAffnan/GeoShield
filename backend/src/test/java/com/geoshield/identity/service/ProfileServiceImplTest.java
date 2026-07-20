package com.geoshield.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ForbiddenException;
import com.geoshield.identity.dto.DeviceTokenRequest;
import com.geoshield.identity.dto.EmergencyContactRequest;
import com.geoshield.identity.dto.EmergencyContactResponse;
import com.geoshield.identity.entity.DeviceToken;
import com.geoshield.identity.entity.EmergencyContact;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.mapper.EmergencyContactMapper;
import com.geoshield.identity.mapper.ProfileMapper;
import com.geoshield.identity.repository.DeviceTokenRepository;
import com.geoshield.identity.repository.EmergencyContactRepository;
import com.geoshield.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private EmergencyContactRepository emergencyContactRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private ProfileMapper profileMapper;
    @Mock private EmergencyContactMapper emergencyContactMapper;

    @Test
    void creatingPrimaryContactClearsExistingPrimaryContact() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        EmergencyContact contact = new EmergencyContact();
        EmergencyContactRequest request = new EmergencyContactRequest("Trusted Contact", "+919876543210", true);
        EmergencyContactResponse response = new EmergencyContactResponse(1L, "Trusted Contact", "+919876543210", true);
        ProfileServiceImpl service = service();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(emergencyContactMapper.toEntity(request)).thenReturn(contact);
        when(emergencyContactRepository.save(contact)).thenReturn(contact);
        when(emergencyContactMapper.toResponse(contact)).thenReturn(response);

        service.createEmergencyContact(userId, request);

        verify(emergencyContactRepository).clearPrimaryForUser(userId);
        verify(emergencyContactRepository).save(contact);
    }

    @Test
    void updatingAnotherUsersContactIsForbidden() {
        UUID requesterId = UUID.randomUUID();
        User owner = user(UUID.randomUUID());
        EmergencyContact contact = new EmergencyContact();
        contact.setUser(owner);
        ProfileServiceImpl service = service();
        when(emergencyContactRepository.findById(1L)).thenReturn(Optional.of(contact));

        assertThatThrownBy(() -> service.updateEmergencyContact(requesterId, 1L,
                new EmergencyContactRequest("Trusted Contact", "+919876543210", false)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void registeringTokenOwnedByAnotherUserIsRejected() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        DeviceToken deviceToken = new DeviceToken(user(UUID.randomUUID()), "fcm-token");
        ProfileServiceImpl service = service();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByFcmToken("fcm-token")).thenReturn(Optional.of(deviceToken));

        assertThatThrownBy(() -> service.registerDeviceToken(userId, new DeviceTokenRequest("fcm-token")))
                .isInstanceOf(ConflictException.class);
    }

    private ProfileServiceImpl service() {
        return new ProfileServiceImpl(userRepository, emergencyContactRepository, deviceTokenRepository, profileMapper,
                emergencyContactMapper);
    }

    private User user(UUID id) {
        User user = new User("tourist", "tourist@example.com", "hash", "Tourist", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
