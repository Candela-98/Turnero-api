package com.turnero.api.mapper;

import com.turnero.api.dto.ProfesionalRequestDto;
import com.turnero.api.model.Profesional;
import org.springframework.stereotype.Component;

@Component
public class ProfesionalMapper {

    public Profesional toEntity(ProfesionalRequestDto dto){
        Profesional profesional = new Profesional();
        profesional.setId(dto.getProfesionalId());
        profesional.setNombre(dto.getNombreProfesional());
        profesional.setEspecialidad(dto.getEspecialidad());
        profesional.setMatricula(dto.getMatricula());

        return profesional;
    }
}
