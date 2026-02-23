package com.turnero.api.service;
import com.turnero.api.model.Servicio;
import com.turnero.api.repository.ServicioRepository;
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
public class ServicioServiceImplTest {
    @Mock
    private ServicioRepository servicioRepository;

    @InjectMocks
    private ServicioServiceImpl servicioService;

    @Test
    void altaServicio_deberiaGuardarYRetornarServicio() {
        Servicio servicio = new Servicio();
        servicio.setNombre("Corte");
        servicio.setDuracionMinutos(30);
        servicio.setPrecio(2500);

        when(servicioRepository.save(servicio)).thenReturn(servicio);

        Servicio resultado = servicioService.altaServicio(servicio);

        assertNotNull(resultado);
        assertEquals("Corte", resultado.getNombre());
        verify(servicioRepository, times(1)).save(servicio);
    }

    @Test
    void buscarServicio_cuandoExiste_retornaServicio() {
        Long id = 1L;
        Servicio servicio = new Servicio();
        servicio.setId(id);

        when(servicioRepository.findById(id)).thenReturn(Optional.of(servicio));

        Servicio resultado = servicioService.buscarServicio(id);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        verify(servicioRepository, times(1)).findById(id);
    }

    @Test
    void buscarServicio_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(servicioRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> servicioService.buscarServicio(id));

        verify(servicioRepository, times(1)).findById(id);
    }

    @Test
    void listarServicios_deberiaRetornarLista() {
        Servicio s1 = new Servicio();
        Servicio s2 = new Servicio();
        when(servicioRepository.findAll()).thenReturn(List.of(s1, s2));

        List<Servicio> lista = servicioService.listarServicio();

        assertEquals(2, lista.size());
        verify(servicioRepository, times(1)).findAll();
    }

    @Test
    void updateServicio_cuandoExiste_actualizaYGuarda() {
        Long id = 1L;

        Servicio existente = new Servicio();
        existente.setId(id);
        existente.setNombre("Corte");
        existente.setDuracionMinutos(20);
        existente.setPrecio(1000);

        Servicio cambios = new Servicio();
        cambios.setNombre("Corte + barba");
        cambios.setDuracionMinutos(45);
        cambios.setPrecio(3000);

        when(servicioRepository.findById(id)).thenReturn(Optional.of(existente));

        servicioService.updateServicio(cambios, id);

        verify(servicioRepository, times(1)).save(existente);
        assertEquals("Corte + barba", existente.getNombre());
        assertEquals(45, existente.getDuracionMinutos());
        assertEquals(3000, existente.getPrecio());
    }

    @Test
    void updateServicio_cuandoNoExiste_lanzaExcepcion_yNoGuarda() {
        Long id = 99L;
        when(servicioRepository.findById(id)).thenReturn(Optional.empty());

        Servicio cambios = new Servicio();
        cambios.setNombre("Corte + barba");

        assertThrows(RuntimeException.class, () -> servicioService.updateServicio(cambios, id));

        verify(servicioRepository, never()).save(any());
    }

    @Test
    void eliminarServicio_cuandoExiste_elimina() {
        Long id = 1L;
        when(servicioRepository.existsById(id)).thenReturn(true);

        servicioService.eliminarServicio(id);

        verify(servicioRepository, times(1)).deleteById(id);
    }

    @Test
    void eliminarServicio_cuandoNoExiste_lanzaExcepcion_yNoElimina() {
        Long id = 99L;
        when(servicioRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> servicioService.eliminarServicio(id));

        verify(servicioRepository, never()).deleteById(anyLong());
    }
}
