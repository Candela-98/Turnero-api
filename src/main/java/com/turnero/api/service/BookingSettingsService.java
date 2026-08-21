package com.turnero.api.service;

import com.turnero.api.dto.BookingSettingsUpdateRequestDto;
import com.turnero.api.model.BookingSettings;

public interface BookingSettingsService {
    BookingSettings getCurrentBookingSettings();

    BookingSettings updateCurrentBookingSettings(BookingSettingsUpdateRequestDto request);
}
