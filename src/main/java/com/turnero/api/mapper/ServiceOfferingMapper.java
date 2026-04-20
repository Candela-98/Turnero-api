package com.turnero.api.mapper;

import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.model.ServiceOffering;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceOfferingMapper {

    ServiceOffering toEntity(ServOfferingRequestDto dto);
}
