package com.turnero.api.service;
import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.repository.ServOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ServiceOfferingServiceImplTest {
    @Mock
    private ServOfferingRepository servOfferingRepository;

    @Mock
    private CurrentBusinessContext currentBusinessContext;

    @InjectMocks
    private ServOfferingServiceImpl servOfferingService;

    @Test
    void saveServiceOffering_shouldSaveAndReturnServiceOffering() {
        Long businessId = 1L;

        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setName("Corte");
        serviceOffering.setDurationMinutes(30);
        serviceOffering.setPriceCents(2500);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.save(serviceOffering)).thenReturn(serviceOffering);

        ServiceOffering result = servOfferingService.saveServiceOffering(serviceOffering);

        assertNotNull(result);
        assertEquals("Corte", result.getName());
        assertEquals(businessId, result.getBusinessId());
        assertEquals(ServiceOfferingStatus.ACTIVE, result.getStatus());

        verify(servOfferingRepository, times(1)).save(serviceOffering);
        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
    }

    @Test
    void findServiceOffering_whenExists_returnsServiceOffering() {
        Long id = 1L;
        Long businessId = 1L;

        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setId(id);
        serviceOffering.setBusinessId(businessId);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(id, businessId))
                .thenReturn(Optional.of(serviceOffering));

        ServiceOffering foundServOffering = servOfferingService.findServiceOffering(id);

        assertNotNull(foundServOffering);
        assertEquals(id, foundServOffering.getId());
        assertEquals(businessId, foundServOffering.getBusinessId());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).findByIdAndBusinessId(id, businessId);
    }

    @Test
    void findServiceOffering_whenNotExists_throwsException() {
        Long id = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> servOfferingService.findServiceOffering(id));

        assertEquals("Service offering not found with ID: " + id, exception.getMessage());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).findByIdAndBusinessId(id, businessId);
    }

    @Test
    void findAllServiceOfferings_shouldReturnList() {
        Long businessId = 1L;

        ServiceOffering s1 = new ServiceOffering();
        s1.setBusinessId(businessId);
        ServiceOffering s2 = new ServiceOffering();
        s2.setBusinessId(businessId);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByBusinessId(businessId)).thenReturn(List.of(s1, s2));

        List<ServiceOffering> listServOffering = servOfferingService.findAllServOffering();

        assertEquals(2, listServOffering.size());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).findByBusinessId(businessId);
    }

    @Test
    void updateServiceOffering_whenExists_updatesAndSaves() {
        Long id = 1L;
        Long businessId = 1L;

        ServiceOffering currentServiceOffering = new ServiceOffering();
        currentServiceOffering.setId(id);
        currentServiceOffering.setBusinessId(businessId);
        currentServiceOffering.setName("Corte");
        currentServiceOffering.setDurationMinutes(20);
        currentServiceOffering.setPriceCents(1000);

        ServOfferingUpdateRequestDto updateRequest = ServOfferingUpdateRequestDto.builder()
                        .name("Corte + barba")
                        .durationMinutes(45)
                        .priceCents(3000)
                        .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(currentServiceOffering));
        when(servOfferingRepository.save(currentServiceOffering)).thenReturn(currentServiceOffering);

        ServiceOffering result = servOfferingService.updateServOffering(updateRequest, id);

        assertEquals("Corte + barba", currentServiceOffering.getName());
        assertEquals(45, currentServiceOffering.getDurationMinutes());
        assertEquals(3000, currentServiceOffering.getPriceCents());
        assertEquals(businessId, currentServiceOffering.getBusinessId());
        assertSame(currentServiceOffering, result);

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).findByIdAndBusinessId(id, businessId);
        verify(servOfferingRepository, times(1)).save(currentServiceOffering);
    }

    @Test
    void updateServiceOffering_whenNotExists_throwsException_andDoesNotSave() {
        Long id = 99L;
        Long businessId = 1L;

        ServOfferingUpdateRequestDto updatedServiceOffering = ServOfferingUpdateRequestDto.builder()
                .name("Corte + barba")
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> servOfferingService.updateServOffering(updatedServiceOffering, id));

        assertEquals("Service offering not found with ID: " + id, exception.getMessage());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).findByIdAndBusinessId(id, businessId);
        verify(servOfferingRepository, never()).save(any());
    }

    @Test
    void deleteServiceOffering_whenExists_deletes() {
        Long id = 1L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.existsByIdAndBusinessId(id, businessId)).thenReturn(true);

        servOfferingService.deleteServOffering(id);

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).existsByIdAndBusinessId(id, businessId);
        verify(servOfferingRepository, times(1)).deleteById(id);
    }

    @Test
    void deleteServiceOffering_whenNotExists_throwsException_andDoesNotDelete() {
        Long id = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.existsByIdAndBusinessId(id, businessId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> servOfferingService.deleteServOffering(id));

        assertEquals("Service offering not found with ID: " + id, exception.getMessage());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(servOfferingRepository, times(1)).existsByIdAndBusinessId(id, businessId);
        verify(servOfferingRepository, never()).deleteById(anyLong());
    }
}


