package com.turnero.api.service;

import com.turnero.api.model.Cliente;
import com.turnero.api.repository.ClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteServiceImpl clienteService;

    @Test
    void altaCliente_deberiaGuardarYRetornarCliente() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Candela");
        cliente.setEmail("candela@mail.com");

        when(clienteRepository.save(cliente)).thenReturn(cliente);

        Cliente resultado = clienteService.altaCliente(cliente);

        assertNotNull(resultado);
        assertEquals("Candela", resultado.getNombre());
        verify(clienteRepository, times(1)).save(cliente);
        System.out.println("Se dio de alta el cliente con id: " + cliente.getId() + ", nombre: " + cliente.getNombre() + ", email: " + cliente.getEmail() + ".");
    }

    @Test
    void buscarCliente_cuandoExiste_retornaCliente() {
        Long id = 1L;
        Cliente cliente = new Cliente();
        cliente.setId(id);

        when(clienteRepository.findById(id)).thenReturn(Optional.of(cliente));

        Cliente resultado = clienteService.buscarCliente(id);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        verify(clienteRepository, times(1)).findById(id);
        System.out.println("Se encontró el cliente con id: " + cliente.getId());
    }

    @Test
    void buscarCliente_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(clienteRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> clienteService.buscarCliente(id));

        verify(clienteRepository, times(1)).findById(id);
        System.out.println("No se encontró el cliente con id: " + id);
    }

    @Test
    void listarClientes_deberiaRetornarLista() {
        Cliente c1 = new Cliente();
        Cliente c2 = new Cliente();
        when(clienteRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Cliente> lista = clienteService.listarClientes();

        assertEquals(2, lista.size());
        verify(clienteRepository, times(1)).findAll();
    }

    @Test
    void updateCliente_cuandoExiste_actualizaYGuarda() {
        Long id = 1L;

        Cliente existente = new Cliente();
        existente.setId(id);
        existente.setNombre("Candela");
        existente.setEmail("Candela@mail.com");
        existente.setTelefono("123");
        existente.setCreadoEn(LocalDateTime.now().minusDays(1));

        Cliente cambios = new Cliente();
        cambios.setNombre("Candela Agustina");
        cambios.setEmail("CandelaAgus@mail.com");
        cambios.setTelefono("456");
        cambios.setCreadoEn(LocalDateTime.now());

        when(clienteRepository.findById(id)).thenReturn(Optional.of(existente));

        clienteService.updateCliente(cambios, id);

        verify(clienteRepository, times(1)).save(existente);
        assertEquals("Candela Agustina", existente.getNombre());
        assertEquals("CandelaAgus@mail.com", existente.getEmail());
        assertEquals("456", existente.getTelefono());
        assertEquals(cambios.getCreadoEn(), existente.getCreadoEn());
        System.out.println("El cliente con id: " + id + " se actualizó correctamente. Nombre: " + existente.getNombre() +
                ", email: " + existente.getEmail() + ", tel: " + existente.getTelefono() + ".");
    }

    @Test
    void updateCliente_cuandoNoExiste_lanzaExcepcion_yNoGuarda() {
        Long id = 99L;
        when(clienteRepository.findById(id)).thenReturn(Optional.empty());

        Cliente cambios = new Cliente();
        cambios.setNombre("Nuevo");

        assertThrows(RuntimeException.class, () -> clienteService.updateCliente(cambios, id));

        verify(clienteRepository, never()).save(any());
        System.out.println("El cliente con id: " + id + " no existe.");
    }

    @Test
    void eliminarCliente_cuandoExiste_elimina() {
        Long id = 1L;
        when(clienteRepository.existsById(id)).thenReturn(true);

        clienteService.eliminarCliente(id);

        verify(clienteRepository, times(1)).deleteById(id);
        System.out.println("El cliente con id: " + id + " fue eliminado exitosamente.");
    }

    @Test
    void eliminarCliente_cuandoNoExiste_lanzaExcepcion() {
        Long id = 99L;
        when(clienteRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> clienteService.eliminarCliente(id));

        verify(clienteRepository, never()).deleteById(anyLong());
        System.out.println("El cliente que desea eliminar no existe.");
    }
}
