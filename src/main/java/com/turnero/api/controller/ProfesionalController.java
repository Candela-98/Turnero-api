package com.turnero.api.controller;

import com.turnero.api.dto.ProfesionalRequestDto;
import com.turnero.api.dto.ServicioRequestDto;
import com.turnero.api.mapper.ProfesionalMapper;
import com.turnero.api.model.Profesional;
import com.turnero.api.model.Servicio;
import com.turnero.api.model.Turno;
import com.turnero.api.service.ProfesionalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profesionales")
public class ProfesionalController {

    private final ProfesionalService profesionalService;
    private final ProfesionalMapper profesionalMapper;

    public ProfesionalController(ProfesionalService profesionalService, ProfesionalMapper profesionalMapper) {
        this.profesionalService = profesionalService;
        this.profesionalMapper = profesionalMapper;
    }

    @PostMapping
    public void altaProfesional(@Valid @RequestBody ProfesionalRequestDto profesionalDto) {
        var profesional = profesionalMapper.toEntity(profesionalDto);
        profesionalService.altaProfesional(profesional);
    }

    @GetMapping("/{id}")
    public Profesional buscarProfesional(@PathVariable Long id) {
        return profesionalService.buscarProfesional(id);
    }

    @PutMapping("/{id}")
    public void updateProfesional(@Valid @RequestBody ProfesionalRequestDto profesionalDto, @PathVariable Long id) {
        var profesional = profesionalMapper.toEntity(profesionalDto);
        profesionalService.updateProfesional(profesional, id);
    }

    @GetMapping
    public List<Profesional> listarProfesional() {
        return profesionalService.listarProfesional();
    }

    @DeleteMapping("/{id}")
    public void eliminarProfesional(@PathVariable Long id) {
        profesionalService.eliminarProfesional(id);
    }
}
