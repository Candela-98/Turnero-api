package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BusinessUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Business;
import com.turnero.api.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class BusinessServiceImpl implements BusinessService {
    private final BusinessRepository businessRepository;
    private final CurrentBusinessContext currentBusinessContext;

    @Override
    public Business getCurrentBusiness() {
        return businessRepository.findById(currentBusinessContext.getCurrentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    @Override
    public Business updateCurrentBusiness(BusinessUpdateRequestDto request) {
        Business business = getCurrentBusiness();
        validateTimezone(request.getTimezone());
        if (request.getName() != null) business.setName(request.getName());
        if (request.getIndustry() != null) business.setIndustry(request.getIndustry());
        if (request.getEmail() != null) business.setEmail(request.getEmail());
        if (request.getPhone() != null) business.setPhone(request.getPhone());
        if (request.getAddress() != null) business.setAddress(request.getAddress());
        if (request.getTimezone() != null) business.setTimezone(request.getTimezone());
        business.setUpdatedAt(LocalDateTime.now());
        return businessRepository.save(business);
    }

    private void validateTimezone(String timezone) {
        if (timezone == null) {
            return;
        }

        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timezone must be a valid IANA timezone");
        }
    }
}
