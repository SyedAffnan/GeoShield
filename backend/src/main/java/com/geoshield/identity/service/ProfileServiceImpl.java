package com.geoshield.identity.service;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.common.exception.ForbiddenException;
import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.identity.dto.DeactivateDeviceTokenRequest;
import com.geoshield.identity.dto.DeviceTokenRequest;
import com.geoshield.identity.dto.EmergencyContactRequest;
import com.geoshield.identity.dto.EmergencyContactResponse;
import com.geoshield.identity.dto.ProfileResponse;
import com.geoshield.identity.dto.UpdateProfileRequest;
import com.geoshield.identity.entity.DeviceToken;
import com.geoshield.identity.entity.EmergencyContact;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.mapper.EmergencyContactMapper;
import com.geoshield.identity.mapper.ProfileMapper;
import com.geoshield.identity.repository.DeviceTokenRepository;
import com.geoshield.identity.repository.EmergencyContactRepository;
import com.geoshield.identity.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {
    private final UserRepository userRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ProfileMapper profileMapper;
    private final EmergencyContactMapper emergencyContactMapper;

    public ProfileServiceImpl(UserRepository userRepository, EmergencyContactRepository emergencyContactRepository,
            DeviceTokenRepository deviceTokenRepository, ProfileMapper profileMapper, EmergencyContactMapper emergencyContactMapper) {
        this.userRepository = userRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.profileMapper = profileMapper;
        this.emergencyContactMapper = emergencyContactMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return profileMapper.toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.fullName(), request.phoneNumber(), request.dateOfBirth(), request.gender(), request.nationality(),
                request.address(), request.profileImageUrl());
        return profileMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> getEmergencyContacts(UUID userId) {
        return emergencyContactRepository.findAllByUserId(userId).stream().map(emergencyContactMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public EmergencyContactResponse createEmergencyContact(UUID userId, EmergencyContactRequest request) {
        User user = findUser(userId);
        if (request.isPrimary()) {
            emergencyContactRepository.clearPrimaryForUser(userId);
        }
        EmergencyContact contact = emergencyContactMapper.toEntity(request);
        contact.setUser(user);
        return emergencyContactMapper.toResponse(emergencyContactRepository.save(contact));
    }

    @Override
    @Transactional
    public EmergencyContactResponse updateEmergencyContact(UUID userId, Long contactId, EmergencyContactRequest request) {
        EmergencyContact contact = findOwnedContact(userId, contactId);
        if (request.isPrimary()) {
            emergencyContactRepository.clearPrimaryForUser(userId);
        }
        emergencyContactMapper.updateEntity(request, contact);
        return emergencyContactMapper.toResponse(contact);
    }

    @Override
    @Transactional
    public void deleteEmergencyContact(UUID userId, Long contactId) {
        emergencyContactRepository.delete(findOwnedContact(userId, contactId));
    }

    @Override
    @Transactional
    public void registerDeviceToken(UUID userId, DeviceTokenRequest request) {
        User user = findUser(userId);
        deviceTokenRepository.findByFcmToken(request.fcmToken()).ifPresentOrElse(existingToken -> {
            if (!existingToken.getUser().getId().equals(userId)) {
                throw new ConflictException("FCM token is already registered to another user");
            }
            deviceTokenRepository.deactivateOtherTokensForUser(userId, request.fcmToken());
            existingToken.activate();
        }, () -> {
            deviceTokenRepository.deactivateAllForUser(userId);
            deviceTokenRepository.save(new DeviceToken(user, request.fcmToken()));
        });
    }

    @Override
    @Transactional
    public void deactivateDeviceToken(UUID userId, DeactivateDeviceTokenRequest request) {
        DeviceToken token = deviceTokenRepository.findByFcmTokenAndUserId(request.fcmToken(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device token not found"));
        token.deactivate();
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private EmergencyContact findOwnedContact(UUID userId, Long contactId) {
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found"));
        if (!contact.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You may only access your own emergency contacts");
        }
        return contact;
    }
}
