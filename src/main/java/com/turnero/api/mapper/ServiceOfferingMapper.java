package com.turnero.api.mapper;

import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.model.ServiceOffering;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ServiceOfferingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "businessId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ServiceOffering toEntity(ServOfferingRequestDto dto);

    ServOfferingResponseDto toResponseDto(ServiceOffering entity);

    List<ServOfferingResponseDto> toResponseDtoList(List<ServiceOffering> entities);
}
