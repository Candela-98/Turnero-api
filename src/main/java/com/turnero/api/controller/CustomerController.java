package com.turnero.api.controller;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.mapper.CustomerMapper;
import com.turnero.api.model.Customer;
import com.turnero.api.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @PostMapping
    public void saveCustomer(@RequestBody CustomerRequestDto customerDto) {
        var customer = customerMapper.toEntity(customerDto);
        customerService.saveCustomer(customer);
    }

    @GetMapping("/{id}")
    public Customer findCustomer(@PathVariable Long id) {
        return customerService.findCustomer(id);
    }

    @PutMapping("/{id}")
    public void updateCustomer(@RequestBody CustomerRequestDto customerDto, @PathVariable Long id) {
        var customer = customerMapper.toEntity(customerDto);
        customerService.updateCustomer(customer, id);
    }

    @GetMapping
    public List<Customer> listCustomer() {
        return customerService.findAllCustomer();
    }

    @DeleteMapping("/{id}")
    public void deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
    }
}
