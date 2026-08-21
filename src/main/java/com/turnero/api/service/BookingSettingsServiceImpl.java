package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BookingSettingsUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.repository.BookingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingSettingsServiceImpl implements BookingSettingsService {
    private static final Set<Integer> ALLOWED_SLOT_INTERVALS = Set.of(15, 30, 45, 60);

    private final BookingSettingsRepository bookingSettingsRepository;
    private final CurrentBusinessContext currentBusinessContext;

    @Override
    public BookingSettings getCurrentBookingSettings() {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return bookingSettingsRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking settings not found"));
    }

    @Override
    public BookingSettings updateCurrentBookingSettings(BookingSettingsUpdateRequestDto request) {
        BookingSettings settings = getCurrentBookingSettings();

        if (request.getSlotIntervalMinutes() != null
                && !ALLOWED_SLOT_INTERVALS.contains(request.getSlotIntervalMinutes())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Slot interval must be one of: 15, 30, 45, 60"
            );
        }

        if (request.getPublicBookingEnabled() != null) {
            settings.setPublicBookingEnabled(request.getPublicBookingEnabled());
        }
        if (request.getBookingWindowDays() != null) {
            settings.setBookingWindowDays(request.getBookingWindowDays());
        }
        if (request.getMinNoticeHours() != null) {
            settings.setMinNoticeHours(request.getMinNoticeHours());
        }
        if (request.getCancellationNoticeHours() != null) {
            settings.setCancellationNoticeHours(request.getCancellationNoticeHours());
        }
        if (request.getSlotIntervalMinutes() != null) {
            settings.setSlotIntervalMinutes(request.getSlotIntervalMinutes());
        }
        if (request.getManualConfirmationEnabled() != null) {
            settings.setManualConfirmationEnabled(request.getManualConfirmationEnabled());
        }

        settings.setRequiresCustomerLogin(false);
        settings.setWhatsappRemindersEnabled(false);
        settings.setUpdatedAt(LocalDateTime.now());
        return bookingSettingsRepository.save(settings);
    }
}
