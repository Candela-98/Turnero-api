package com.turnero.api.controller;

import com.turnero.api.dto.TurnoRequestDto;
import com.turnero.api.mapper.TurnoMapper;
import com.turnero.api.model.Turno;
import com.turnero.api.service.TurnoService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/turnos")
public class TurnoController {

    private final TurnoService turnoService;
    private final TurnoMapper turnoMapper;

    public TurnoController(TurnoService turnoService, TurnoMapper turnoMapper) {
        this.turnoService = turnoService;
        this.turnoMapper = turnoMapper;
    }

    @PostMapping
    public void reservarTurno(@RequestBody TurnoRequestDto turnoDto) {

        var turno = turnoMapper.toEntity(turnoDto);
        turnoService.reservarTurno(turno);
    }

    @GetMapping
    public List<Turno> listarTurnos() {
        return turnoService.listarTurnos();
    }

    @GetMapping("/{id}")
    public Turno buscarTurno(@PathVariable Long id) {
        return turnoService.buscarTurno(id);
    }

    @GetMapping("/{id}")
    public void updateTurno(@RequestBody TurnoRequestDto turnoDto, @PathVariable Long id) {
        var turno = turnoMapper.toEntity(turnoDto);
        turnoService.updateTurno(turno, id);
    }

    @DeleteMapping("{/id}")
    public void eliminarTurno(@PathVariable Long id){
        turnoService.eliminarTurno(id);
    }

}
