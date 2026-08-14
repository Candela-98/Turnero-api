package com.turnero.api.service;

import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
import com.turnero.api.model.ServiceOffering;

import java.util.List;

public interface ServOfferingService {

    ServiceOffering saveServiceOffering(ServiceOffering serviceOffering);

    List<ServiceOffering> findAllServOffering();

    ServiceOffering findServiceOffering(Long id);

    ServiceOffering updateServOffering(ServOfferingUpdateRequestDto servOfferigUpdateDto, Long id);

    void deleteServOffering(Long id);
}
