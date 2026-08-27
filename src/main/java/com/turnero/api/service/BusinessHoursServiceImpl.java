package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BusinessHoursDayRequestDto;
import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.BusinessHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessHoursServiceImpl implements BusinessHoursService {
    private final BusinessHoursRepository businessHoursRepository;
    private final CurrentBusinessContext currentBusinessContext;

    @Override
    public List<BusinessHours> getCurrentBusinessHours() {
        return sortWeek(businessHoursRepository.findAllByBusinessId(currentBusinessContext.getCurrentBusinessId()));
    }

    @Override
    @Transactional
    public List<BusinessHours> replaceCurrentBusinessHours(BusinessHoursReplaceRequestDto request) {
        List<BusinessHoursDayRequestDto> hours = request.getHours();
        validateWeek(hours);

        Long businessId = currentBusinessContext.getCurrentBusinessId();
        businessHoursRepository.deleteAllByBusinessId(businessId);

        List<BusinessHours> replacement = hours.stream()
                .map(hour -> toEntity(businessId, hour))
                .toList();

        return sortWeek(businessHoursRepository.saveAll(replacement));
    }

    private void validateWeek(List<BusinessHoursDayRequestDto> hours) {
        if (hours.size() != 7) {
            throw new IllegalArgumentException("Business hours must contain exactly 7 days");
        }

        Set<DayOfWeek> days = hours.stream()
                .map(BusinessHoursDayRequestDto::getDayOfWeek)
                .collect(Collectors.toSet());

        if (days.size() != 7) {
            throw new IllegalArgumentException("Business hours must contain all 7 unique days");
        }

        for (BusinessHoursDayRequestDto hour : hours) {
            if (Boolean.FALSE.equals(hour.getIsClosed())) {
                if (hour.getOpensAt() == null) {
                    throw new IllegalArgumentException("Opening time is required when the day is open");
                }
                if (hour.getClosesAt() == null) {
                    throw new IllegalArgumentException("Closing time is required when the day is open");
                }
                if (!hour.getOpensAt().isBefore(hour.getClosesAt())) {
                    throw new IllegalArgumentException("Opening time must be before closing time for open days");
                }
            }
        }
    }

    private BusinessHours toEntity(Long businessId, BusinessHoursDayRequestDto hour) {
        boolean isClosed = Boolean.TRUE.equals(hour.getIsClosed());
        return BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(hour.getDayOfWeek())
                .opensAt(isClosed ? null : hour.getOpensAt())
                .closesAt(isClosed ? null : hour.getClosesAt())
                .isClosed(isClosed)
                .build();
    }

    private List<BusinessHours> sortWeek(List<BusinessHours> hours) {
        return hours.stream()
                .sorted(Comparator.comparingInt(hour -> hour.getDayOfWeek().ordinal()))
                .toList();
    }
}
