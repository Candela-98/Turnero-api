package com.turnero.api.service;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.ServiceOffering;
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

    @InjectMocks
    private ServOfferingServiceImpl servOfferingService;

    @Test
    void saveServiceOffering_shouldSaveAndReturnServiceOffering() {
        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setName("Corte");
        serviceOffering.setDurationMinutes(30);
        serviceOffering.setPriceCents(2500);

        when(servOfferingRepository.save(serviceOffering)).thenReturn(serviceOffering);

        ServiceOffering result = servOfferingService.saveServiceOffering(serviceOffering);

        assertNotNull(result);
        assertEquals("Corte", result.getName());
        verify(servOfferingRepository, times(1)).save(serviceOffering);
    }

    @Test
    void findServiceOffering_whenExists_returnsServiceOffering() {
        Long id = 1L;
        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setId(id);

        when(servOfferingRepository.findById(id)).thenReturn(Optional.of(serviceOffering));

        ServiceOffering foundServOffering = servOfferingService.findServiceOffering(id);

        assertNotNull(foundServOffering);
        assertEquals(1L, foundServOffering.getId());
        verify(servOfferingRepository, times(1)).findById(id);
    }

    @Test
    void findServiceOffering_whenNotExists_throwsException() {
        Long id = 99L;
        when(servOfferingRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> servOfferingService.findServiceOffering(id));

        assertEquals("Service offering not found with ID: " + id, exception.getMessage());

        verify(servOfferingRepository, times(1)).findById(id);
    }

    @Test
    void findAllServiceOfferings_shouldReturnList() {
        ServiceOffering s1 = new ServiceOffering();
        ServiceOffering s2 = new ServiceOffering();
        when(servOfferingRepository.findAll()).thenReturn(List.of(s1, s2));

        List<ServiceOffering> listServOffering = servOfferingService.findAllServOffering();

        assertEquals(2, listServOffering.size());
        verify(servOfferingRepository, times(1)).findAll();
    }

    @Test
    void updateServiceOffering_whenExists_updatesAndSaves() {
        Long id = 1L;

        ServiceOffering currentServiceOffering = new ServiceOffering();
        currentServiceOffering.setId(id);
        currentServiceOffering.setName("Corte");
        currentServiceOffering.setDurationMinutes(20);
        currentServiceOffering.setPriceCents(1000);

        ServiceOffering updatedServiceOffering = new ServiceOffering();
        updatedServiceOffering.setName("Corte + barba");
        updatedServiceOffering.setDurationMinutes(45);
        updatedServiceOffering.setPriceCents(3000);

        when(servOfferingRepository.findById(id)).thenReturn(Optional.of(currentServiceOffering));

        servOfferingService.updateServOffering(updatedServiceOffering, id);

        verify(servOfferingRepository, times(1)).save(currentServiceOffering);
        assertEquals("Corte + barba", currentServiceOffering.getName());
        assertEquals(45, currentServiceOffering.getDurationMinutes());
        assertEquals(3000, currentServiceOffering.getPriceCents());
    }

    @Test
    void updateServiceOffering_whenNotExists_throwsException_andDoesNotSave() {
        Long id = 99L;
        when(servOfferingRepository.findById(id)).thenReturn(Optional.empty());

        ServiceOffering updatedServiceOffering = new ServiceOffering();
        updatedServiceOffering.setName("Corte + barba");

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> servOfferingService.updateServOffering(updatedServiceOffering, id));

        assertEquals("Service offering not found with ID: " + id, exception.getMessage());

        verify(servOfferingRepository, never()).save(any());
    }

    @Test
    void deleteServiceOffering_whenExists_deletes() {
        Long id = 1L;
        when(servOfferingRepository.existsById(id)).thenReturn(true);

        servOfferingService.deleteServOffering(id);

        verify(servOfferingRepository, times(1)).deleteById(id);
    }

    @Test
    void deleteServiceOffering_whenNotExists_throwsException_andDoesNotDelete() {
        Long id = 99L;
        when(servOfferingRepository.existsById(id)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> servOfferingService.deleteServOffering(id));

        assertEquals("Service offering not found with ID: " + id, exception.getMessage());

        verify(servOfferingRepository, never()).deleteById(anyLong());
    }
}


