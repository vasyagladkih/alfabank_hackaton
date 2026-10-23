package ru.alfahackaton.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.alfahackaton.dto.UserProfile;
import ru.alfahackaton.dto.UserRole;
import ru.alfahackaton.persistance.entity.UserEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "role", expression = "java(mapRole(entity.role()))")
    UserProfile toDto(UserEntity entity);

    default UserRole mapRole(String role) {
        return role != null ? UserRole.fromValue(role) : null;
    }
}