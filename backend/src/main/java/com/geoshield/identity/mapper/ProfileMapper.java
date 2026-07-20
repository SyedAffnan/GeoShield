package com.geoshield.identity.mapper;

import com.geoshield.identity.dto.ProfileResponse;
import com.geoshield.identity.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ProfileMapper {
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "role", source = "role.name")
    ProfileResponse toResponse(User user);
}
