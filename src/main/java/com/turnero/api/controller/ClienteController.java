package com.turnero.api.controller;

import com.turnero.api.dto.ClienteRequestDto;
import com.turnero.api.dto.ProfesionalRequestDto;
import com.turnero.api.mapper.ClienteMapper;
import com.turnero.api.model.Cliente;
import com.turnero.api.model.Profesional;
import com.turnero.api.model.Turno;
import com.turnero.api.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    public ClienteController(ClienteService clienteService, ClienteMapper clienteMapper) {
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
    }

    @PostMapping
    public ResponseEntity<Cliente> altaCliente(@Valid @RequestBody ClienteRequestDto clienteDto) {
        var cliente = clienteMapper.toEntity(clienteDto);
        var clienteGuardado = clienteService.altaCliente(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteGuardado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> buscarCliente(@PathVariable Long id) {
        Cliente cliente = clienteService.buscarCliente(id);
        return ResponseEntity.ok(cliente);
    }

    @PutMapping("/{id}")
    public ResponseEntity updateCliente(@Valid @RequestBody ClienteRequestDto clienteDto, @PathVariable Long id) {
        var cliente = clienteMapper.toEntity(clienteDto);
        clienteService.updateCliente(cliente, id);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<Cliente>> listarClientes() {
        var clientes = clienteService.listarClientes();
        return ResponseEntity.status(HttpStatus.OK).body(clientes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity eliminarCliente(@PathVariable Long id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

    }
}
