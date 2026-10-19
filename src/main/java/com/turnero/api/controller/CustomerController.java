package com.turnero.api.controller;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.dto.CustomerPageResponseDto;
import com.turnero.api.dto.CustomerUpdateRequestDto;
import com.turnero.api.mapper.CustomerMapper;
import com.turnero.api.model.Customer;
import com.turnero.api.openapi.*;
import com.turnero.api.service.CustomerService;
import com.turnero.api.service.CustomerSort;
import com.turnero.api.model.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            description = "Crea un cliente ACTIVE del negocio autenticado. Nombre y phone_number obligatorios (máximo 100/30); email válido opcional (150) y notas internas opcionales (2000). Acepta phone como alias compatible. Campos inválidos responden 400."
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
            description = "Edita parcialmente el cliente del negocio autenticado. Omisión/null conserva valores; email y notas vacíos los borran. Nombre/teléfono enviados no pueden estar vacíos. Clientes ajenos o inexistentes responden 404."
    )
    @ApiUpdateResponses
    @PatchMapping("/{id}")
    public ResponseEntity<CustomerResponseDto> updateCustomer(@Valid @RequestBody CustomerUpdateRequestDto customerDto, @PathVariable Long id) {
        var updatedCustomer = customerService.updateCustomer(customerDto, id);
        var customerResponseDto = customerMapper.toResponseDto(updatedCustomer);
        return ResponseEntity.ok(customerResponseDto);
    }

    @Operation(
            summary = "Listar clientes",
            description = "Busca por nombre, email o teléfono del negocio autenticado. El teléfono ignora formato. Página desde 0; tamaño 1–100 (20 por defecto); búsqueda hasta 100 caracteres; orden name,asc o name,desc con id ascendente como desempate. Parámetros inválidos devuelven 400.")
    @ApiFindAllResponses
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Parámetros de listado inválidos")
    @GetMapping
    public ResponseEntity<CustomerPageResponseDto> listCustomer(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = CustomerSort.DEFAULT_PARAMETER) String sort) {
        var customers = customerService.listCustomers(q, status, page, size, CustomerSort.fromParameter(sort));
        return ResponseEntity.ok(new CustomerPageResponseDto(
                customerMapper.toSummaryResponseDtoList(customers.getContent()),
                new CustomerPageResponseDto.PageInfo(customers.getNumber(), customers.getSize(),
                        customers.getTotalElements(), customers.getTotalPages())));
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
