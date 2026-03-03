package com.turnero.api.service;

import com.turnero.api.model.Customer;
import com.turnero.api.repository.CustomerRepository;
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
public class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    void saveCustomer_shouldSaveAndReturnCustomer() {
        Customer customer = new Customer();
        customer.setName("Candela");
        customer.setEmail("candela@mail.com");

        when(customerRepository.save(customer)).thenReturn(customer);

        Customer result = customerService.saveCustomer(customer);

        assertNotNull(result);
        assertEquals("Candela", result.getName());
        verify(customerRepository, times(1)).save(customer);
    }

    @Test
    void findCustomer() {
        Long id = 1L;
        Customer customer = new Customer();
        customer.setId(id);

        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        Customer result = customerService.findCustomer(id);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(customerRepository, times(1)).findById(id);
    }

    @Test
    void findCustomer_whenNotExists_throwException() {
        Long id = 99L;
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> customerService.findCustomer(id));

        verify(customerRepository, times(1)).findById(id);
    }

    @Test
    void findAllCustomer_shouldReturnList() {
        Customer c1 = new Customer();
        Customer c2 = new Customer();
        when(customerRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Customer> list = customerService.findAllCustomer();

        assertEquals(2, list.size());
        verify(customerRepository, times(1)).findAll();
    }

    @Test
    void updateCustomer_whenExists_UpdateAndSave() {
        Long id = 1L;

        Customer current = new Customer();
        current.setId(id);
        current.setName("Candela");
        current.setEmail("Candela@mail.com");
        current.setPhoneNumber("123");
        current.setCreatedIn(LocalDateTime.now().minusDays(1));

        Customer updatedCustomer = new Customer();
        updatedCustomer.setName("Candela Agustina");
        updatedCustomer.setEmail("CandelaAgus@mail.com");
        updatedCustomer.setPhoneNumber("456");
        updatedCustomer.setCreatedIn(LocalDateTime.now());

        when(customerRepository.findById(id)).thenReturn(Optional.of(current));

        customerService.updateCustomer(updatedCustomer, id);

        verify(customerRepository, times(1)).save(current);
        assertEquals("Candela Agustina", current.getName());
        assertEquals("CandelaAgus@mail.com", current.getEmail());
        assertEquals("456", current.getPhoneNumber());
        assertEquals(updatedCustomer.getCreatedIn(), current.getCreatedIn());
    }

    @Test
    void updateCustomer_cuandoNoExiste_lanzaExcepcion_yNoGuarda() {
        Long id = 99L;
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        Customer updatedCustomer = new Customer();
        updatedCustomer.setName("Nuevo");

        assertThrows(RuntimeException.class, () -> customerService.updateCustomer(updatedCustomer, id));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void deleteCustomer_whenExists_deletes() {
        Long id = 1L;
        when(customerRepository.existsById(id)).thenReturn(true);

        customerService.deleteCustomer(id);

        verify(customerRepository, times(1)).deleteById(id);
    }

    @Test
    void deleteCustomer_whenNotExists_throwException() {
        Long id = 99L;
        when(customerRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> customerService.deleteCustomer(id));

        verify(customerRepository, never()).deleteById(anyLong());
    }
}
