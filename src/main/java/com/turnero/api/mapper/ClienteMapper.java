package com.turnero.api.mapper;

import com.turnero.api.dto.ClienteRequestDto;
import com.turnero.api.model.Cliente;
import org.springframework.stereotype.Component;

@Component
public class ClienteMapper {

    public Cliente toEntity(ClienteRequestDto dtoCliente){
        Cliente cliente = new Cliente();

        cliente.setId(dtoCliente.getClienteId());
        cliente.setNombre(dtoCliente.getNombreCliente());
        cliente.setEmail(dtoCliente.getEmail());
        cliente.setTelefono(dtoCliente.getTelCliente());
        cliente.setCreadoEn(dtoCliente.getFechaCreacion());

        return cliente;
    }
}
