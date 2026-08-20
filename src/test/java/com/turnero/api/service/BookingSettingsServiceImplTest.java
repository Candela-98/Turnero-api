package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BookingSettingsUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.repository.BookingSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingSettingsServiceImplTest {
    @Mock private BookingSettingsRepository bookingSettingsRepository;
    @Mock private CurrentBusinessContext currentBusinessContext;
    @InjectMocks private BookingSettingsServiceImpl bookingSettingsService;

    @Test
    void getCurrentBookingSettings_returnsSettingsForCurrentBusiness() {
        BookingSettings settings = settings();
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(bookingSettingsRepository.findByBusinessId(1L)).thenReturn(Optional.of(settings));

        assertEquals(settings, bookingSettingsService.getCurrentBookingSettings());
        verify(bookingSettingsRepository).findByBusinessId(1L);
    }

    @Test
    void getCurrentBookingSettings_whenMissing_throwsNotFound() {
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(bookingSettingsRepository.findByBusinessId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingSettingsService.getCurrentBookingSettings());
    }

    @Test
    void updateCurrentBookingSettings_updatesEditableFieldsAndKeepsMvpOnlyFieldsDisabled() {
        BookingSettings settings = settings();
        settings.setRequiresCustomerLogin(true);
        settings.setWhatsappRemindersEnabled(true);
        LocalDateTime createdAt = settings.getCreatedAt();
        BookingSettingsUpdateRequestDto request = new BookingSettingsUpdateRequestDto();
        request.setPublicBookingEnabled(false);
        request.setBookingWindowDays(14);
        request.setMinNoticeHours(4);
        request.setCancellationNoticeHours(2);
        request.setSlotIntervalMinutes(15);
        request.setManualConfirmationEnabled(true);
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(bookingSettingsRepository.findByBusinessId(1L)).thenReturn(Optional.of(settings));
        when(bookingSettingsRepository.save(settings)).thenReturn(settings);

        BookingSettings updated = bookingSettingsService.updateCurrentBookingSettings(request);

        assertFalse(updated.isPublicBookingEnabled());
        assertEquals(14, updated.getBookingWindowDays());
        assertEquals(4, updated.getMinNoticeHours());
        assertEquals(2, updated.getCancellationNoticeHours());
        assertEquals(15, updated.getSlotIntervalMinutes());
        assertTrue(updated.isManualConfirmationEnabled());
        assertFalse(updated.isRequiresCustomerLogin());
        assertFalse(updated.isWhatsappRemindersEnabled());
        assertEquals(createdAt, updated.getCreatedAt());
        verify(bookingSettingsRepository).save(settings);
    }

    @Test
    void updateCurrentBookingSettings_whenSlotIntervalIsNotAllowed_rejectsRequest() {
        BookingSettingsUpdateRequestDto request = new BookingSettingsUpdateRequestDto();
        request.setSlotIntervalMinutes(20);
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(bookingSettingsRepository.findByBusinessId(1L)).thenReturn(Optional.of(settings()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingSettingsService.updateCurrentBookingSettings(request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(bookingSettingsRepository, never()).save(any());
    }

    private BookingSettings settings() {
        return BookingSettings.builder()
                .businessId(1L)
                .publicBookingEnabled(true)
                .requiresCustomerLogin(false)
                .bookingWindowDays(7)
                .minNoticeHours(3)
                .cancellationNoticeHours(3)
                .slotIntervalMinutes(30)
                .manualConfirmationEnabled(false)
                .whatsappRemindersEnabled(false)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }
}
