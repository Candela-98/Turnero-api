package com.turnero.api.service;
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
    private ServOfferingServiceImpl servicioService;

    @Test
    void altaServicio_deberiaGuardarYRetornarServicio() {
        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setName("Corte");
        serviceOffering.setDurationMinutes(30);
        serviceOffering.setPrice(2500);

        when(servOfferingRepository.save(serviceOffering)).thenReturn(serviceOffering);

        ServiceOffering resultado = servicioService.saveServiceOffering(serviceOffering);

        assertNotNull(resultado);
        assertEquals("Corte", resultado.getName());
        verify(servOfferingRepository, times(1)).save(serviceOffering);
    }

    @Test
    void buscarServicio_cuandoExiste_retornaServicio() {
        Long id = 1L;
        ServiceOffering serviceOffering = new ServiceOffering();
        serviceOffering.setId(id);

        when(servOfferingRepository.findById(id)).thenReturn(Optional.of(serviceOffering));

        ServiceOffering resultado = servicioService.findServiceOffering(id);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        verify(servOfferingRepository, times(1)).findById(id);
    }

    @Test
    void buscarServicio_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(servOfferingRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> servicioService.findServiceOffering(id));

        verify(servOfferingRepository, times(1)).findById(id);
    }

    @Test
    void listarServicios_deberiaRetornarLista() {
        ServiceOffering s1 = new ServiceOffering();
        ServiceOffering s2 = new ServiceOffering();
        when(servOfferingRepository.findAll()).thenReturn(List.of(s1, s2));

        List<ServiceOffering> lista = servicioService.findAllServOffering();

        assertEquals(2, lista.size());
        verify(servOfferingRepository, times(1)).findAll();
    }

    @Test
    void updateServicio_cuandoExiste_actualizaYGuarda() {
        Long id = 1L;

        ServiceOffering existente = new ServiceOffering();
        existente.setId(id);
        existente.setName("Corte");
        existente.setDurationMinutes(20);
        existente.setPrice(1000);

        ServiceOffering cambios = new ServiceOffering();
        cambios.setName("Corte + barba");
        cambios.setDurationMinutes(45);
        cambios.setPrice(3000);

        when(servOfferingRepository.findById(id)).thenReturn(Optional.of(existente));

        servicioService.updateServOffering(cambios, id);

        verify(servOfferingRepository, times(1)).save(existente);
        assertEquals("Corte + barba", existente.getName());
        assertEquals(45, existente.getDurationMinutes());
        assertEquals(3000, existente.getPrice());
    }

    @Test
    void updateServicio_cuandoNoExiste_lanzaExcepcion_yNoGuarda() {
        Long id = 99L;
        when(servOfferingRepository.findById(id)).thenReturn(Optional.empty());

        ServiceOffering cambios = new ServiceOffering();
        cambios.setName("Corte + barba");

        assertThrows(RuntimeException.class, () -> servicioService.updateServOffering(cambios, id));

        verify(servOfferingRepository, never()).save(any());
    }

    @Test
    void eliminarServicio_cuandoExiste_elimina() {
        Long id = 1L;
        when(servOfferingRepository.existsById(id)).thenReturn(true);

        servicioService.deleteServOffering(id);

        verify(servOfferingRepository, times(1)).deleteById(id);
    }

    @Test
    void eliminarServicio_cuandoNoExiste_lanzaExcepcion_yNoElimina() {
        Long id = 99L;
        when(servOfferingRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> servicioService.deleteServOffering(id));

        verify(servOfferingRepository, never()).deleteById(anyLong());
    }
}
