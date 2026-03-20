package com.turnero.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.mapper.CustomerMapper;
import com.turnero.api.model.Customer;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CustomerMapper customerMapper;

    @Autowired
    CustomerService customerService;

    @Autowired
    CustomerRepository customerRepository;

    @BeforeEach
    void cleanDb() {
        customerRepository.deleteAll();
    }

    @Test
    void saveCustomer_whenRequestIsValid_persistsCustomer_andReturns200() throws Exception {
        // Given
        LocalDateTime expectedDate = LocalDateTime.of(2026, 2, 24, 21, 0);
        CustomerRequestDto dto = getCustomerRequestDto();

        // When
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Then
        List<Customer> customers = customerRepository.findAll();

        assertThat(customers).hasSize(1);
        Customer saved = customers.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Juan Perez");
        assertThat(saved.getEmail()).isEqualTo("juan@mail.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("1122334455");
        assertThat(saved.getCreatedIn()).isEqualTo(expectedDate);
    }

    @Test
    void saveCustomer_whenNameIsNull_returns400() throws Exception {
        // Given
        CustomerRequestDto dto = getCustomerRequestDto();
        dto.setNameCustomer(null);

        // When + Then
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // Then
        assertThat(customerRepository.findAll()).isEmpty();
    }

    @Test
    void findCustomer_whenCustomerExists_returns200AndCustomer() throws Exception {

        // Given
        LocalDateTime expectedDate = LocalDateTime.of(2026, 2, 24, 21, 0);
        Customer customer = getCustomer();

        Customer saved = customerRepository.save(customer);

        // When
        MvcResult result = mockMvc.perform(get("/api/customers/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();

        Customer response = objectMapper.readValue(json, Customer.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(saved.getName()).isEqualTo("Juan Perez");
        assertThat(saved.getEmail()).isEqualTo("juan@mail.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("1122334455");
        assertThat(saved.getCreatedIn()).isEqualTo(expectedDate);
    }

    @Test
    void findCustomer_whenCustomerDoesNotExist_returns404() throws Exception {
        Long id = 999L;

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCustomer_whenRequestIsValid_updatesCustomer_andReturns204() throws Exception {
        // Given
        Customer customer = getCustomer();

        Customer saved = customerRepository.save(customer);

        CustomerRequestDto dto = getCustomerRequestDto();
        dto.setNameCustomer("Juan Updated");
        dto.setEmail("new@mail.com");

        // When
        mockMvc.perform(put("/api/customers/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        Customer updated = customerRepository.findById(saved.getId()).orElseThrow();

        assertThat(updated.getName()).isEqualTo("Juan Updated");
        assertThat(updated.getEmail()).isEqualTo("new@mail.com");
    }

    @Test
    void udpateCustomer_whenNameIsNull_returns400() throws Exception {
        // Given
        CustomerRequestDto dto = getCustomerRequestDto();
        dto.setCustomerId(12L);
        dto.setNameCustomer(null);

        // When + Then
        mockMvc.perform(put("/api/customers/{id}", dto.getCustomerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listCustomer_whenCustomersExist_returns200AndCustomerList() throws Exception {
        // Given
        Customer customer1 = getCustomer();

        Customer customer2 = new Customer();
        customer2.setName("Maria Gomez");
        customer2.setEmail("maria@mail.com");
        customer2.setPhoneNumber("1199999999");
        customer2.setCreatedIn(LocalDateTime.of(2026, 2, 25, 10, 30));

        customerRepository.save(customer1);
        customerRepository.save(customer2);

        // When
        MvcResult result = mockMvc.perform(get("/api/customers")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();

        List<Customer> response = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(response).hasSize(2);
        assertThat(response)
                .extracting(Customer::getName)
                .containsExactlyInAnyOrder("Juan Perez", "Maria Gomez");
        assertThat(response)
                .extracting(Customer::getEmail)
                .containsExactlyInAnyOrder("juan@mail.com", "maria@mail.com");
    }

    @Test
    void listCustomer_whenNoCustomersExist_returns200AndEmptyList() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/customers")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();

        List<Customer> response = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(response).isEmpty();
    }

    @Test
    void deleteCustomer_whenCustomerExists_deletesCustomer_andReturns204() throws Exception {
        // Given
        Customer customer = getCustomer();

        Customer saved = customerRepository.save(customer);
        Long id = saved.getId();

        // When + Then
        mockMvc.perform(delete("/api/customers/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(customerRepository.existsById(id)).isFalse();
    }

    @Test
    void deleteCustomer_whenCustomerDoesNotExist_returns404() throws Exception {
        // Given
        Long id = 999L;

        // When + Then
        mockMvc.perform(delete("/api/customers/{id}", id))
                .andExpect(status().isNotFound());
    }

    private CustomerRequestDto getCustomerRequestDto() {
        CustomerRequestDto dto = new CustomerRequestDto();
        dto.setNameCustomer("Juan Perez");
        dto.setEmail("juan@mail.com");
        dto.setPhoneCustomer("1122334455");
        dto.setCreationDate(LocalDateTime.of(2026, 2, 24, 21, 0));

        return dto;
    }

    private Customer getCustomer() {
        Customer customer = new Customer();
        customer.setName("Juan Perez");
        customer.setEmail("juan@mail.com");
        customer.setPhoneNumber("1122334455");
        customer.setCreatedIn(LocalDateTime.of(2026, 2, 24, 21, 0));

        return customer;
    }

}
