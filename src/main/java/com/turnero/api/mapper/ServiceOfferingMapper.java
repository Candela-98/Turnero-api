package com.turnero.api.mapper;

import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.model.ServiceOffering;
import org.springframework.stereotype.Component;

@Component
public class ServiceOfferingMapper {
    public ServiceOffering toEntity(ServOfferingRequestDto dto) {
        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setId(dto.getId());
        serviceOffering.setName(dto.getName());
        serviceOffering.setDurationMinutes(dto.getDurationMinutes());
        serviceOffering.setPrice(dto.getPrice());

        return serviceOffering;
    }
}
