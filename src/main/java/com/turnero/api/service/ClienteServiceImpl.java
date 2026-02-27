package com.turnero.api.service;

import com.turnero.api.model.Cliente;
import com.turnero.api.model.Turno;
import com.turnero.api.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteServiceImpl implements ClienteService{

    private final ClienteRepository clienteRepository;

    public ClienteServiceImpl(ClienteRepository clienteRepository){
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Cliente altaCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    @Override
    public Cliente buscarCliente(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    @Override
    public void updateCliente(Cliente cliente, Long id) {
        Cliente clienteExiste = buscarCliente(id);

        clienteExiste.setNombre(cliente.getNombre());
        clienteExiste.setEmail(cliente.getEmail());
        clienteExiste.setTelefono(cliente.getTelefono());
        clienteExiste.setCreadoEn(cliente.getCreadoEn());

        clienteRepository.save(clienteExiste);
        System.out.println("Cliente con ID " + id + " actualizado exitosamente.");
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @Override
    public void eliminarCliente(Long id) {

        if(clienteRepository.existsById(id)) {
            clienteRepository.deleteById(id);
            System.out.println("Cliente con ID " + id + " eliminado exitosamente.");
        } else {
            throw new RuntimeException("Cliente no encontrado");
        }
    }
}
