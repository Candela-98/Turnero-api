package com.turnero.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
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
        LocalDateTime expectedDate = LocalDateTime.of(2026, 4, 29, 20, 07);
        CustomerRequestDto dto = getCustomerRequestDto();


        // When
        MvcResult result = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        List<Customer> customers = customerRepository.findAll();

        assertThat(customers).hasSize(1);
        Customer saved = customers.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Juan Perez");
        assertThat(saved.getEmail()).isEqualTo("juan@mail.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("1122334455");
        assertThat(saved.getCreatedIn()).isNotNull();

        String json = result.getResponse().getContentAsString();
        CustomerResponseDto response = objectMapper.readValue(json, CustomerResponseDto.class);

        assertThat(response.getCustomerId()).isEqualTo(saved.getId());
        assertThat(response.getNameCustomer()).isEqualTo("Juan Perez");
        assertThat(response.getEmail()).isEqualTo("juan@mail.com");
        assertThat(response.getPhoneCustomer()).isEqualTo("1122334455");
        assertThat(response.getCreatedIn()).isNotNull();
    }

    @Test
    void saveCustomer_whenNameIsBlank_returns400() throws Exception {
        // Given
        CustomerRequestDto dto = getCustomerRequestDto();
        dto.setNameCustomer("");

        // When + Then
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validations.nameCustomer").value("The customer's name is required."));

        // Then
        assertThat(customerRepository.findAll()).isEmpty();
    }

    @Test
    void saveCustomer_whenEmailIsInvalid_returns400() throws Exception {
        // Given
        CustomerRequestDto dto = getCustomerRequestDto();
        dto.setEmail("invalid-email");

        // When + Then
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validations.email").value("The customer's email address must be valid."));

        assertThat(customerRepository.findAll()).isEmpty();
    }

    @Test
    void findCustomer_whenCustomerExists_returns200AndCustomer() throws Exception {

        // Given
        Customer customer = getCustomer();
        Customer saved = customerRepository.save(customer);

        // When
        MvcResult result = mockMvc.perform(get("/api/customers/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();

        CustomerResponseDto response = objectMapper.readValue(json, CustomerResponseDto.class);

        assertThat(response.getCustomerId()).isEqualTo(saved.getId());
        assertThat(saved.getName()).isEqualTo("Juan Perez");
        assertThat(saved.getEmail()).isEqualTo("juan@mail.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("1122334455");
        assertThat(saved.getCreatedIn()).isEqualTo(saved.getCreatedIn());
    }

    @Test
    void findCustomer_whenCustomerDoesNotExist_returns404() throws Exception {
        Long id = 999L;

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found"));
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
    void udpateCustomer_whenNameIsBlank_returns400() throws Exception {
        // Given
        CustomerRequestDto dto = getCustomerRequestDto();
        Customer saved = customerRepository.save(getCustomer());
        dto.setNameCustomer("");

        // When + Then
        mockMvc.perform(put("/api/customers/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validations.nameCustomer").value("The customer's name is required."));
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

        List<CustomerResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(response)
                .extracting(CustomerResponseDto::getNameCustomer)
                .containsExactlyInAnyOrder("Juan Perez", "Maria Gomez");

        assertThat(response)
                .extracting(CustomerResponseDto::getEmail)
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
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer not found with ID: " + id));
    }

    private CustomerRequestDto getCustomerRequestDto() {
        return CustomerRequestDto.builder()
                .nameCustomer("Juan Perez")
                .email("juan@mail.com")
                .phoneCustomer("1122334455")
                .build();
    }

    private Customer getCustomer() {
        return Customer.builder()
                .name("Juan Perez")
                .email("juan@mail.com")
                .phoneNumber("1122334455")
                .createdIn(LocalDateTime.of(2026, 2, 24, 21, 0))
                .build();
    }

}
