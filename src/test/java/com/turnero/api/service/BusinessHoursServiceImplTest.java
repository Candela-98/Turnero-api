package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BusinessHoursDayRequestDto;
import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.BusinessHoursRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BusinessHoursServiceImplTest {
    @Mock private BusinessHoursRepository businessHoursRepository;
    @Mock private CurrentBusinessContext currentBusinessContext;
    @InjectMocks private BusinessHoursServiceImpl businessHoursService;

    @Test
    void getCurrentBusinessHours_usesCurrentBusinessAndReturnsMondayFirst() {
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessHoursRepository.findAllByBusinessId(1L)).willReturn(List.of(
                entity(DayOfWeek.SUNDAY, true), entity(DayOfWeek.MONDAY, false)
        ));

        List<BusinessHours> result = businessHoursService.getCurrentBusinessHours();

        assertThat(result).extracting(BusinessHours::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
        verify(businessHoursRepository).findAllByBusinessId(1L);
    }

    @Test
    void replaceCurrentBusinessHours_replacesAllDaysForCurrentBusiness() {
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessHoursRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        List<BusinessHours> result = businessHoursService.replaceCurrentBusinessHours(fullWeek());

        assertThat(result).hasSize(7);
        assertThat(result.getFirst().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.getFirst().getBusinessId()).isEqualTo(1L);
        assertThat(result.get(5).getOpensAt()).isNull();
        assertThat(result.get(5).getClosesAt()).isNull();
        verify(businessHoursRepository).deleteAllByBusinessId(1L);
        verify(businessHoursRepository).saveAll(anyList());
    }

    @Test
    void replaceCurrentBusinessHours_whenWeekIsIncomplete_doesNotModifyHours() {
        BusinessHoursReplaceRequestDto request = fullWeek();
        request.setHours(request.getHours().subList(0, 6));

        assertThatThrownBy(() -> businessHoursService.replaceCurrentBusinessHours(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Business hours must contain exactly 7 days");

        verify(businessHoursRepository, never()).deleteAllByBusinessId(1L);
        verify(businessHoursRepository, never()).saveAll(anyList());
    }

    @Test
    void replaceCurrentBusinessHours_whenRangeIsInvalid_doesNotModifyHours() {
        BusinessHoursReplaceRequestDto request = fullWeek();
        request.getHours().getFirst().setOpensAt(LocalTime.of(18, 0));
        request.getHours().getFirst().setClosesAt(LocalTime.of(18, 0));

        assertThatThrownBy(() -> businessHoursService.replaceCurrentBusinessHours(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opening time must be before closing time for open days");

        verify(businessHoursRepository, never()).deleteAllByBusinessId(1L);
        verify(businessHoursRepository, never()).saveAll(anyList());
    }

    private BusinessHoursReplaceRequestDto fullWeek() {
        List<BusinessHoursDayRequestDto> hours = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            BusinessHoursDayRequestDto hour = new BusinessHoursDayRequestDto();
            hour.setDayOfWeek(day);
            boolean closed = day == DayOfWeek.SATURDAY;
            hour.setIsClosed(closed);
            hour.setOpensAt(closed ? LocalTime.of(8, 0) : LocalTime.of(9, 0));
            hour.setClosesAt(closed ? LocalTime.of(12, 0) : LocalTime.of(18, 0));
            hours.add(hour);
        }
        BusinessHoursReplaceRequestDto request = new BusinessHoursReplaceRequestDto();
        request.setHours(hours);
        return request;
    }

    private BusinessHours entity(DayOfWeek day, boolean closed) {
        return BusinessHours.builder().businessId(1L).dayOfWeek(day).isClosed(closed).build();
    }
}
