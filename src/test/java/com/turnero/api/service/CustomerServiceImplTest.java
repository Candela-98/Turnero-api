package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.CustomerUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Customer;
import com.turnero.api.model.enums.CustomerStatus;
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

    @Mock
    private CurrentBusinessContext currentBusinessContext;

    @Test
    void saveCustomer_shouldSaveAndReturnCustomer() {
        Customer customer = new Customer();
        customer.setName("Candela");
        customer.setEmail("candela@mail.com");

        when(customerRepository.save(customer)).thenReturn(customer);
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);

        Customer result = customerService.saveCustomer(customer);

        assertNotNull(result);
        assertEquals("Candela", result.getName());
        assertEquals(1L, result.getBusinessId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(customerRepository, times(1)).save(customer);
    }

    @Test
    void findCustomer() {
        Long id = 1L;
        Long businessId = 1L;
        Customer customer = new Customer();
        customer.setId(id);
        customer.setBusinessId(businessId);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(customer));

        Customer result = customerService.findCustomer(id);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(businessId, result.getBusinessId());
        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(customerRepository, times(1)).findByIdAndBusinessId(id, businessId);
        verify(customerRepository, never()).findById(id);
    }

    @Test
    void findCustomer_whenNotExists_throwException() {
        Long id = 99L;
        Long businessId = 1L;
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> customerService.findCustomer(id));

        assertEquals("Customer not found with ID: " + id, exception.getMessage());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(customerRepository, times(1)).findByIdAndBusinessId(id, businessId);
        verify(customerRepository, never()).findById(id);
    }

    @Test
    void findCustomer_whenOutsideBusinessScope_throwsException() {
        Long id = 1L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> customerService.findCustomer(id)
        );

        assertEquals("Customer not found with ID: " + id, exception.getMessage());
        verify(customerRepository).findByIdAndBusinessId(id, businessId);
        verify(customerRepository, never()).findById(id);
    }

    @Test
    void findAllCustomer_shouldReturnList() {
        Long businessId = 1L;

        Customer c1 = new Customer();
        Customer c2 = new Customer();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findAllByBusinessId(businessId)).thenReturn(List.of(c1, c2));

        List<Customer> list = customerService.findAllCustomer();

        assertEquals(2, list.size());

        verify(currentBusinessContext, times(1)).getCurrentBusinessId();
        verify(customerRepository, times(1)).findAllByBusinessId(businessId);
        verify(customerRepository, times(1)).findAllByBusinessId(1L);
    }

    @Test
    void updateCustomer_whenExists_UpdateAndSave() {
        Long id = 1L;
        Long businessId = 1L;
        Long userId = 5L;
        LocalDateTime originalCreatedAt = LocalDateTime.now().minusDays(1);

        Customer current = new Customer();
        current.setId(id);
        current.setBusinessId(businessId);
        current.setUserId(userId);
        current.setName("Candela");
        current.setEmail("Candela@mail.com");
        current.setPhoneNumber("123");
        current.setInternalNotes("Old notes");
        current.setStatus(CustomerStatus.ACTIVE);
        current.setCreatedAt(originalCreatedAt);
        current.setUpdatedAt(originalCreatedAt);

        CustomerUpdateRequestDto updatedCustomer = CustomerUpdateRequestDto.builder()
                .name("Candela Agustina")
                .email("CandelaAgus@mail.com")
                .phoneNumber("456")
                .internalNotes("Updated notes")
                .status(CustomerStatus.INACTIVE)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(current));
        when(customerRepository.save(current)).thenReturn(current);

        Customer result = customerService.updateCustomer(updatedCustomer, id);

        verify(customerRepository, times(1)).save(current);
        assertEquals("Candela Agustina", current.getName());
        assertEquals("CandelaAgus@mail.com", current.getEmail());
        assertEquals("456", current.getPhoneNumber());
        assertEquals("Updated notes", current.getInternalNotes());
        assertEquals(CustomerStatus.INACTIVE, current.getStatus());
        assertEquals(id, current.getId());
        assertEquals(businessId, current.getBusinessId());
        assertEquals(userId, current.getUserId());
        assertEquals(originalCreatedAt, current.getCreatedAt());
        assertNotEquals(originalCreatedAt, current.getUpdatedAt());
        assertSame(current, result);
        verify(customerRepository, never()).findById(id);
    }

    @Test
    void updateCustomer_whenPartialRequest_updatesOnlyProvidedFields() {
        Long id = 1L;
        Long businessId = 1L;
        LocalDateTime originalCreatedAt = LocalDateTime.now().minusDays(1);

        Customer current = Customer.builder()
                .id(id)
                .businessId(businessId)
                .userId(20L)
                .name("Candela")
                .email("candela@mail.com")
                .phoneNumber("123")
                .internalNotes("Old notes")
                .status(CustomerStatus.ACTIVE)
                .createdAt(originalCreatedAt)
                .updatedAt(originalCreatedAt)
                .build();

        CustomerUpdateRequestDto updateRequest = CustomerUpdateRequestDto.builder()
                .internalNotes("Cliente frecuente")
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(current));
        when(customerRepository.save(current)).thenReturn(current);

        customerService.updateCustomer(updateRequest, id);

        assertEquals("Candela", current.getName());
        assertEquals("candela@mail.com", current.getEmail());
        assertEquals("123", current.getPhoneNumber());
        assertEquals("Cliente frecuente", current.getInternalNotes());
        assertEquals(CustomerStatus.ACTIVE, current.getStatus());
        assertEquals(id, current.getId());
        assertEquals(businessId, current.getBusinessId());
        assertEquals(20L, current.getUserId());
        assertEquals(originalCreatedAt, current.getCreatedAt());
    }

    @Test
    void updateCustomer_whenEntityDoesNotExist_throwsException_andDoesNotSave() {
        Long id = 99L;
        Long businessId = 1L;
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        CustomerUpdateRequestDto updatedCustomer = CustomerUpdateRequestDto.builder()
                .name("Nuevo")
                .build();

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> customerService.updateCustomer(updatedCustomer, id));

        assertEquals("Customer not found with ID: " + id, exception.getMessage());

        verify(customerRepository, never()).save(any());
        verify(customerRepository, never()).findById(id);
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

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> customerService.deleteCustomer(id));

        assertEquals("Customer not found with ID: 99", exception.getMessage());

        verify(customerRepository, never()).deleteById(anyLong());
    }
}
