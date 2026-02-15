package com.turnero.api.mapper;

import com.turnero.api.dto.ServicioRequestDto;
import com.turnero.api.dto.TurnoRequestDto;
import com.turnero.api.model.Servicio;
import com.turnero.api.model.Turno;
import org.springframework.stereotype.Component;

@Component
public class ServicioMapper {
    public Servicio toEntity(ServicioRequestDto dto) {
        Servicio servicio = new Servicio();
        servicio.setId(dto.getServicioId());
        servicio.setNombre(dto.getNombre());
        servicio.setDuracionMinutos(dto.getDuracionMinutos());
        servicio.setPrecio(dto.getPrecio());

        return servicio;
    }
}
