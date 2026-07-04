package com.turnero.api.controller;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.mapper.CustomerMapper;
import com.turnero.api.model.Customer;
import com.turnero.api.openapi.*;
import com.turnero.api.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Customers",
        description = "Endpoints para gestionar clientes"
)
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @Operation(
            summary = "Crear cliente",
            description = "Crea un nuevo cliente en el sistema"
    )
    @ApiCreateResponses
    @PostMapping
    public ResponseEntity<CustomerResponseDto> saveCustomer(@Valid @RequestBody CustomerRequestDto customerDto) {
        var customer = customerMapper.toEntity(customerDto);
        var customerSaved = customerService.saveCustomer(customer);
        var customerResponseDto = customerMapper.toResponseDto(customerSaved);

        return ResponseEntity.status(HttpStatus.CREATED).body(customerResponseDto);
    }

    @Operation(
            summary = "Obtener cliente por ID",
            description = "Obtiene los detalles de un cliente específico utilizando su ID"
    )
    @ApiFindByIdResponses
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDto> findCustomer(@PathVariable Long id) {
        var customer = customerService.findCustomer(id);
        var customerResponseDto = customerMapper.toResponseDto(customer);
        return ResponseEntity.ok(customerResponseDto);
    }

    @Operation(
            summary = "Actualizar cliente",
            description = "Actualiza los detalles de un cliente específico utilizando su ID"
    )
    @ApiUpdateResponses
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCustomer(@Valid @RequestBody CustomerRequestDto customerDto, @PathVariable Long id) {
        var customer = customerMapper.toEntity(customerDto);
        customerService.updateCustomer(customer, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar clientes",
            description = "Obtiene una lista de todos los clientes registrados en el sistema")
    @ApiFindAllResponses
    @GetMapping
    public ResponseEntity<List<CustomerResponseDto>> listCustomer() {
        var customers = customerService.findAllCustomer();
        var customersResponseDto = customerMapper.toResponseDtoList(customers);
        return ResponseEntity.ok(customersResponseDto);
    }

    @Operation(
            summary = "Eliminar cliente",
            description = "Elimina un cliente específico utilizando su ID"
    )
    @ApiDeleteResponses
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
