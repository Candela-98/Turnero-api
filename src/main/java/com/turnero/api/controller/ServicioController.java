package com.turnero.api.controller;


import com.turnero.api.dto.ServicioRequestDto;
import com.turnero.api.dto.TurnoRequestDto;
import com.turnero.api.mapper.ServicioMapper;
import com.turnero.api.model.Servicio;
import com.turnero.api.model.Turno;
import com.turnero.api.service.ServicioService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/turnos")
public class ServicioController {

    private final ServicioService servicioService;
    private final ServicioMapper servicioMapper;

    public ServicioController(ServicioService servicioService, ServicioMapper servicioMapper) {
        this.servicioService = servicioService;
        this.servicioMapper = servicioMapper;
    }

    @PostMapping
    public void altaServicio(@RequestBody ServicioRequestDto servicioDto) {
        var servicio = servicioMapper.toEntity(servicioDto);
        servicioService.altaServicio(servicio);
    }

    @GetMapping("/{id}")
    public Servicio buscarServicio(@PathVariable Long id) {
        return servicioService.buscarServicio(id);
    }

    @PutMapping("/{id}")
    public void updateServicio(@RequestBody ServicioRequestDto servicioDto, @PathVariable Long id) {
        var servicio = servicioMapper.toEntity(servicioDto);
        servicioService.updateServicio(servicio, id);
    }

    @GetMapping
    public List<Servicio> listarServicio() {
        return servicioService.listarServicio();
    }

    @DeleteMapping("/{id}")
    public void eliminarServicio(@PathVariable Long id) {
        servicioService.eliminarServicio(id);
    }
}
