package com.turnero.api.controller;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.mapper.CustomerMapper;
import com.turnero.api.model.Customer;
import com.turnero.api.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDto> saveCustomer(@Valid @RequestBody CustomerRequestDto customerDto) {
        var customer = customerMapper.toEntity(customerDto);
        var customerSaved = customerService.saveCustomer(customer);
        var customerResponseDto = customerMapper.toResponseDto(customerSaved);

        return ResponseEntity.status(HttpStatus.CREATED).body(customerResponseDto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDto> findCustomer(@PathVariable Long id) {
        var customer = customerService.findCustomer(id);
        var customerResponseDto = customerMapper.toResponseDto(customer);
        return ResponseEntity.ok(customerResponseDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCustomer(@Valid @RequestBody CustomerRequestDto customerDto, @PathVariable Long id) {
        var customer = customerMapper.toEntity(customerDto);
        customerService.updateCustomer(customer, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponseDto>> listCustomer() {
        var customers = customerService.findAllCustomer();
        var customersResponseDto = customerMapper.toResponseDtoList(customers);
        return ResponseEntity.ok(customersResponseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
