package com.turnero.api.mapper;

import com.turnero.api.dto.BookingSettingsResponseDto;
import com.turnero.api.model.BookingSettings;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingSettingsMapper {
    BookingSettingsResponseDto toResponseDto(BookingSettings entity);
}
