package com.turnero.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.dto.CustomerUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.CustomerMapper;
import com.turnero.api.model.Customer;
import com.turnero.api.model.enums.CustomerStatus;
import com.turnero.api.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerMapper customerMapper;

    @MockitoBean
    private CustomerService customerService;

    private static final String BASE_URL = "/api/v1/customers";

    @Test
    void saveCustomer_whenRequestIsValid_returns200() throws Exception {
        // Given
        Long id = 12L;
        var dto = getCustomerDTO();
        var entity = getCustomerEntity(id);
        var responseDto = getCustomerResponseDto(id);

        given(customerMapper.toEntity(any(CustomerRequestDto.class))).willReturn(entity);
        given(customerService.saveCustomer(entity)).willReturn(entity);
        given(customerMapper.toResponseDto(entity)).willReturn(responseDto);

        // When
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.name").value("Juan"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.phone_number").value("1122334455"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.internal_notes").value("Prefiere corte bajo."))
                .andExpect(jsonPath("$.created_at").value("2026-02-24T21:00:00"))
                .andExpect(jsonPath("$.updated_at").value("2026-02-24T22:00:00"));

        // Assert
        then(customerMapper).should().toEntity(any(CustomerRequestDto.class));
        then(customerService).should().saveCustomer(entity);
        then(customerMapper).should().toResponseDto(entity);
    }

    @Test
    void saveCustomer_whenNameIsBlank_returns400() throws Exception {
        // Given:
        Long id = 12L;
        var dto = getCustomerDTO();
        dto.setName("");

        // When + Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.error").value("Bad Request"))
                        .andExpect(jsonPath("$.message").value("Validation error"))
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                        .andExpect(jsonPath("$.details[0].field").value("name"))
                        .andExpect(jsonPath("$.details[0].message").exists())
                        .andExpect(jsonPath("$.path").value(BASE_URL))
                        .andExpect(jsonPath("$.timestamp").exists());

        then(customerService).shouldHaveNoInteractions();
    }

    @Test
    void saveCustomer_whenEmailIsInvalid_returns400() throws Exception {
        // Given
        Long id = 12L;
        var dto = getCustomerDTO();
        dto.setEmail("invalid-email");

        // When + Then
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        then(customerService).shouldHaveNoInteractions();
    }

    @Test
    void retrieveCustomer_whenCustomerExists_returnsCustomerData() throws Exception {
        // Given
        Long id = 12L;
        var customer = getCustomerEntity(id);
        var responseDto = getCustomerResponseDto(id);

        given(customerService.findCustomer(id)).willReturn(customer);
        given(customerMapper.toResponseDto(customer)).willReturn(responseDto);

        // When + Assert
        mockMvc.perform(get(BASE_URL + "/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.name").value("Juan"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.phone_number").value("1122334455"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.internal_notes").value("Prefiere corte bajo."))
                .andExpect(jsonPath("$.created_at").value("2026-02-24T21:00:00"))
                .andExpect(jsonPath("$.updated_at").value("2026-02-24T22:00:00"));

        then(customerService).should().findCustomer(id);
        then(customerMapper).should().toResponseDto(customer);
    }

    @Test
    void retrieveCustomer_whenCustomerDoesNotExist_returns404() throws Exception {
        // Given
        given(customerService.findCustomer(999L)).willThrow(new ResourceNotFoundException("Customer not found with ID: 999"));

        // When + Then
        mockMvc.perform(get(BASE_URL + "/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found with ID: 999"));

        then(customerService).should().findCustomer(999L);
        then(customerMapper).shouldHaveNoInteractions();
    }

    @Test
    void retrieveCustomer_whenIdIsInvalid_returnsBadRequest() throws Exception {
        // Given
        var id = "invalidId";

        // When
        mockMvc.perform(get(BASE_URL  + "/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter type"));

        // Assert
        then(customerService).should(never()).findCustomer(any());
    }

    @Test
    void updateCustomer_whenRequestIsValid_returns200() throws Exception {
        // Given
        Long id = 12L;
        var dto = CustomerUpdateRequestDto.builder()
                .name("Santiago Actualizado")
                .internalNotes("Cliente frecuente")
                .status(CustomerStatus.INACTIVE)
                .build();
        var entity = getCustomerEntity(id);
        entity.setName("Santiago Actualizado");
        entity.setInternalNotes("Cliente frecuente");
        entity.setStatus(CustomerStatus.INACTIVE);
        entity.setUpdatedAt(LocalDateTime.of(2026, 2, 24, 23, 0));
        var responseDto = CustomerResponseDto.builder()
                .id(id)
                .name("Santiago Actualizado")
                .email("juan@mail.com")
                .phoneNumber("1122334455")
                .status(CustomerStatus.INACTIVE)
                .internalNotes("Cliente frecuente")
                .createdAt(LocalDateTime.of(2026, 2, 24, 21, 0))
                .updatedAt(LocalDateTime.of(2026, 2, 24, 23, 0))
                .build();

        given(customerService.updateCustomer(any(CustomerUpdateRequestDto.class), eq(id))).willReturn(entity);
        given(customerMapper.toResponseDto(entity)).willReturn(responseDto);

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.name").value("Santiago Actualizado"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.phone_number").value("1122334455"))
                .andExpect(jsonPath("$.internal_notes").value("Cliente frecuente"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.updated_at").value("2026-02-24T23:00:00"));

        then(customerService).should().updateCustomer(any(CustomerUpdateRequestDto.class), eq(id));
        then(customerMapper).should().toResponseDto(entity);
    }

    @Test
    void updateCustomer_whenNameIsBlank_returns400() throws Exception {
        // Given
        Long id = 12L;
        var dto = CustomerUpdateRequestDto.builder()
                .name("")
                .build();

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/{id}", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL + "/12"))
                .andExpect(jsonPath("$.timestamp").exists());

        then(customerService).shouldHaveNoInteractions();
    }

    @Test
    void updateCustomer_whenCustomerDoesNotExist_returns404() throws Exception {
        // Given
        var dto = CustomerUpdateRequestDto.builder()
                .name("Santiago Actualizado")
                .build();

        given(customerService.updateCustomer(any(CustomerUpdateRequestDto.class), eq(999L)))
                .willThrow(new ResourceNotFoundException("Customer not found with ID: 999"));

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found with ID: 999"));

        then(customerService).should().updateCustomer(any(CustomerUpdateRequestDto.class), eq(999L));
        then(customerMapper).shouldHaveNoInteractions();
    }

    @Test
    void findAllCustomers_whenCustomersExist_returns200AndList() throws Exception {
        // Given
        Long id = 12L;
        var customer1 = getCustomerEntity(id);
        var customer2 = getCustomerEntity(13L);

        var response1 = getCustomerSummaryResponseDto(id);
        var response2 = getCustomerSummaryResponseDto(13L);

        given(customerService.findAllCustomer())
                .willReturn(List.of(customer1, customer2));
        given(customerMapper.toSummaryResponseDtoList(List.of(customer1, customer2)))
                .willReturn(List.of(response1, response2));

        // When + Then
        mockMvc.perform(get(BASE_URL)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].name").value("Juan"))
                .andExpect(jsonPath("$[0].internal_notes").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(13))
                .andExpect(jsonPath("$[1].name").value("Juan"));

        then(customerService).should().findAllCustomer();
        then(customerMapper).should().toSummaryResponseDtoList(List.of(customer1, customer2));
    }

    @Test
    void deleteCustomer_whenCustomerExists_returns200() throws Exception {
        Long id = 12L;

        // When + Then
        mockMvc.perform(delete(BASE_URL + "/{id}", id))
                .andExpect(status().isNoContent());

        then(customerService).should().deleteCustomer(id);
        then(customerService).shouldHaveNoMoreInteractions();
    }

    @Test
    void deleteCustomer_withNonExistentId_shouldReturn404() throws Exception {
        // Given
        var id = "invalidId";

        // When
        mockMvc.perform(delete(BASE_URL + "/{id}", id)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter type"));

        // Then
        then(customerService).should(never()).deleteCustomer(any());
    }

    private CustomerRequestDto getCustomerDTO() {
        return CustomerRequestDto.builder()
                .name("Juan")
                .email("juan@mail.com")
                .phone("1122334455")
                .build();
    }

    private CustomerResponseDto getCustomerResponseDto(Long id) {
        return CustomerResponseDto.builder()
                .id(id)
                .name("Juan")
                .email("juan@mail.com")
                .phoneNumber("1122334455")
                .status(CustomerStatus.ACTIVE)
                .internalNotes("Prefiere corte bajo.")
                .createdAt(LocalDateTime.of(2026, 2, 24, 21, 0))
                .updatedAt(LocalDateTime.of(2026, 2, 24, 22, 0))
                .build();
    }

    private CustomerResponseDto getCustomerSummaryResponseDto(Long id) {
        return CustomerResponseDto.builder()
                .id(id)
                .name("Juan")
                .email("juan@mail.com")
                .phoneNumber("1122334455")
                .status(CustomerStatus.ACTIVE)
                .build();
    }

    private Customer getCustomerEntity(Long id) {
        return Customer.builder()
                .id(id)
                .name("Juan")
                .email("juan@mail.com")
                .phoneNumber("1122334455")
                .status(CustomerStatus.ACTIVE)
                .internalNotes("Prefiere corte bajo.")
                .createdAt(LocalDateTime.of(2026, 2, 24, 21, 0))
                .updatedAt(LocalDateTime.of(2026, 2, 24, 22, 0))
                .build();

    }

}
