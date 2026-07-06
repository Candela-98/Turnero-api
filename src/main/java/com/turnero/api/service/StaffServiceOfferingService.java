package com.turnero.api.service;

import com.turnero.api.dto.ServOfferingResponseDto;

import java.util.List;

public interface StaffServiceOfferingService {

    List<ServOfferingResponseDto> getServiceOfferings(Long staffMemberId);

    void replaceServiceOfferings(Long staffMemberId, List<Long> serviceOfferingIds);
}
