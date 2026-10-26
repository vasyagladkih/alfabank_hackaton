package ru.alfahackaton.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.LoanPurpose;
import ru.alfahackaton.persistance.entity.ApplicationEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ApplicationMapper {

    @Mapping(target = "purpose", expression = "java(mapPurpose(entity.purpose()))")
    @Mapping(target = "status", expression = "java(mapStatus(entity.status()))")
    @Mapping(target = "schedule", ignore = true)
    ApplicationResponse toDto(ApplicationEntity entity);

    default LoanPurpose mapPurpose(String purpose) {
        return purpose != null ? LoanPurpose.fromValue(purpose) : null;
    }

    default ApplicationStatus mapStatus(String status) {
        return status != null ? ApplicationStatus.fromValue(status) : null;
    }
}