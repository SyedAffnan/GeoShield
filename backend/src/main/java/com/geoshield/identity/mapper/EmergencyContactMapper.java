package com.geoshield.identity.mapper;

import com.geoshield.identity.dto.EmergencyContactRequest;
import com.geoshield.identity.dto.EmergencyContactResponse;
import com.geoshield.identity.entity.EmergencyContact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EmergencyContactMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "primary", source = "isPrimary")
    EmergencyContact toEntity(EmergencyContactRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "primary", source = "isPrimary")
    void updateEntity(EmergencyContactRequest request, @MappingTarget EmergencyContact contact);

    @Mapping(target = "contactId", source = "id")
    @Mapping(target = "isPrimary", source = "primary")
    EmergencyContactResponse toResponse(EmergencyContact contact);
}
