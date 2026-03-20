package com.turnero.api.service;

import com.turnero.api.model.ServiceOffering;

import java.util.List;

public interface ServOfferingService {

    ServiceOffering saveServiceOffering(ServiceOffering serviceOffering);

    List<ServiceOffering> findAllServOffering();

    ServiceOffering findServiceOffering(Long id);

    void updateServOffering(ServiceOffering serviceOffering, Long id);

    void deleteServOffering(Long id);
}
