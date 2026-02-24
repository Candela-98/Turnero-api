package com.turnero.api.service;

import com.turnero.api.model.Cliente;
import com.turnero.api.model.Turno;

import java.util.List;

public interface ClienteService {

    Cliente altaCliente(Cliente cliente);

    Cliente buscarCliente(Long id);

    List<Cliente> listarClientes();

    void updateCliente(Cliente cliente, Long id);

    void eliminarCliente(Long id);
}
