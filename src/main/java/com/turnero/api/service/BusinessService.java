package com.turnero.api.service;

import com.turnero.api.dto.BusinessUpdateRequestDto;
import com.turnero.api.model.Business;

public interface BusinessService {
    Business getCurrentBusiness();
    Business updateCurrentBusiness(BusinessUpdateRequestDto request);
}
