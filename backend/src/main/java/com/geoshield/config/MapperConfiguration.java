package com.geoshield.config;

import com.geoshield.identity.mapper.EmergencyContactMapper;
import com.geoshield.identity.mapper.ProfileMapper;
import com.geoshield.identity.mapper.UserMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfiguration {
    @Bean
    UserMapper userMapper() {
        return Mappers.getMapper(UserMapper.class);
    }

    @Bean
    ProfileMapper profileMapper() {
        return Mappers.getMapper(ProfileMapper.class);
    }

    @Bean
    EmergencyContactMapper emergencyContactMapper() {
        return Mappers.getMapper(EmergencyContactMapper.class);
    }
}
