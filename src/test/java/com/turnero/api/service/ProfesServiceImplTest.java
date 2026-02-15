package com.turnero.api.service;

import com.turnero.api.model.Profesional;
import com.turnero.api.repository.ProfesionalRepository;
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
public class ProfesServiceImplTest {
    @Mock
    private ProfesionalRepository profesionalRepository;

    @InjectMocks
    private ProfesionalServiceImpl profesionalService;

    @Test
    void altaProfesional_deberiaGuardarYRetornarProfesional() {
        Profesional profesional = new Profesional();
        profesional.setNombre("Juan");
        profesional.setEspecialidad("Barbero");
        profesional.setMatricula("MAT-123");

        when(profesionalRepository.save(profesional)).thenReturn(profesional);

        Profesional resultado = profesionalService.altaProfesional(profesional);

        assertNotNull(resultado);
        assertEquals("Juan", resultado.getNombre());
        verify(profesionalRepository, times(1)).save(profesional);
        System.out.println("Se dio de alta el profesional: " + profesional.getNombre() +
                " con especialidad: " + profesional.getEspecialidad() + ". Su matricula es: " + profesional.getMatricula());
    }

    @Test
    void buscarProfesional_cuandoExiste_retornaProfesional() {
        Long id = 1L;
        Profesional profesional = new Profesional();
        profesional.setId(id);

        when(profesionalRepository.findById(id)).thenReturn(Optional.of(profesional));

        Profesional resultado = profesionalService.buscarProfesional(id);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        verify(profesionalRepository, times(1)).findById(id);
        System.out.println("Se encontró el barbero con id: " + profesional.getId() + ", nombre: " + profesional.getNombre());
    }

    @Test
    void buscarProfesional_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(profesionalRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> profesionalService.buscarProfesional(id));

        verify(profesionalRepository, times(1)).findById(id);
        System.out.println("No se encontró el profesional con id: " + id);
    }

    @Test
    void listarProfesional_deberiaRetornarLista() {
        Profesional p1 = new Profesional();
        Profesional p2 = new Profesional();
        when(profesionalRepository.findAll()).thenReturn(List.of(p1, p2));

        List<Profesional> lista = profesionalService.listarProfesional();

        assertEquals(2, lista.size());
        verify(profesionalRepository, times(1)).findAll();
        System.out.println("Los profesionales encontrados son: " + lista);
    }

    @Test
    void updateProfesional_cuandoExiste_actualizaYGuarda() {
        Long id = 1L;

        Profesional existente = new Profesional();
        existente.setId(id);
        existente.setNombre("Carlos");
        existente.setEspecialidad("Barbero");
        existente.setMatricula("MAT-788");

        Profesional cambios = new Profesional();
        cambios.setNombre("Juan Carlos");
        cambios.setEspecialidad("Barbero plus");
        cambios.setMatricula("MAT-778");

        when(profesionalRepository.findById(id)).thenReturn(Optional.of(existente));

        profesionalService.updateProfesional(cambios, id);

        verify(profesionalRepository, times(1)).save(existente);
        assertEquals("Juan Carlos", existente.getNombre());
        assertEquals("Barbero plus", existente.getEspecialidad());
        assertEquals("MAT-778", existente.getMatricula());
    }

    @Test
    void updateProfesional_cuandoNoExiste_lanzaExcepcion_yNoGuarda() {
        Long id = 99L;
        when(profesionalRepository.findById(id)).thenReturn(Optional.empty());

        Profesional cambios = new Profesional();
        cambios.setNombre("Nuevo");

        assertThrows(RuntimeException.class, () -> profesionalService.updateProfesional(cambios, id));

        verify(profesionalRepository, never()).save(any());
    }

    @Test
    void eliminarProfesional_cuandoExiste_elimina() {
        Long id = 1L;
        when(profesionalRepository.existsById(id)).thenReturn(true);

        profesionalService.eliminarProfesional(id);

        verify(profesionalRepository, times(1)).deleteById(id);
        System.out.println("El profesional con id: " + id + " fue eliminado exitosamente.");
    }

    @Test
    void eliminarProfesional_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(profesionalRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> profesionalService.eliminarProfesional(id));

        verify(profesionalRepository, never()).deleteById(anyLong());
        System.out.println("El profesional con id: " + id + " no existe");
    }
}
