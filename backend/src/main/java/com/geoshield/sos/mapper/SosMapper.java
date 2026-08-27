package com.geoshield.sos.mapper;

import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SosMapper {

    @Mapping(target = "sosId", source = "id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "phoneNumber", source = "user.phoneNumber")
    @Mapping(target = "assignedResponderId", source = "assignedResponder.id")
    @Mapping(target = "triggeredAt", source = "createdAt")
    SosResponse toResponse(SosRequest sosRequest);
}