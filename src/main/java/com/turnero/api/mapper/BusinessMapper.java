package com.turnero.api.mapper;

import com.turnero.api.dto.BusinessResponseDto;
import com.turnero.api.model.Business;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BusinessMapper {
    BusinessResponseDto toResponseDto(Business business);
}
