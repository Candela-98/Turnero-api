package com.turnero.api.service;

import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ServOfferingService {

    ServiceOffering saveServiceOffering(ServiceOffering serviceOffering);

    Page<ServiceOffering> listServiceOfferings(String q, String category, ServiceOfferingStatus status, int page, int size, String sort);

    List<String> listCategories();

    ServiceOffering findServiceOffering(Long id);

    ServiceOffering updateServOffering(ServOfferingUpdateRequestDto servOfferigUpdateDto, Long id);

    void deleteServOffering(Long id);
}
