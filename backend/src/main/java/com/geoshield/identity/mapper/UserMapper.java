package com.geoshield.identity.mapper;

import com.geoshield.identity.dto.RegisterResponse;
import com.geoshield.identity.dto.UserSummaryResponse;
import com.geoshield.identity.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "role", source = "role.name")
    RegisterResponse toRegisterResponse(User user);

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "role", source = "role.name")
    UserSummaryResponse toUserSummaryResponse(User user);
}
