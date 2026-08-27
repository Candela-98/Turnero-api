package com.turnero.api.service;

import com.turnero.api.dto.BusinessHoursReplaceRequestDto;
import com.turnero.api.model.BusinessHours;

import java.util.List;

public interface BusinessHoursService {
    List<BusinessHours> getCurrentBusinessHours();

    List<BusinessHours> replaceCurrentBusinessHours(BusinessHoursReplaceRequestDto request);
}
