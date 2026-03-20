package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.service.ServOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ServOfferingController.class)
public class ServiceOfferingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ServOfferingService servOfferingService;
    @MockitoBean
    private ServiceOfferingMapper sMapper;

    private ServOfferingRequestDto validDto() {
        ServOfferingRequestDto dto = new ServOfferingRequestDto();
        dto.setId(1L);
        dto.setName("Corte y barba");
        dto.setDurationMinutes(60);
        dto.setPrice(10000.0);
        return dto;
    }

    private ServiceOffering servicioConId(long id){
        ServiceOffering s = new ServiceOffering();
        s.setId(id);
        s.setName("Corte y barba");
        s.setDurationMinutes(60);
        s.setPrice(10000.0);
        return s;
    }

    @Test
    void saveServOffering_ok_shouldReturn200_andCallService() throws Exception {
        ServOfferingRequestDto dto = validDto();
        ServiceOffering entity = new ServiceOffering();
        when(sMapper.toEntity(any(ServOfferingRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/service-offerings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(sMapper).toEntity(any(ServOfferingRequestDto.class));
        verify(servOfferingService).saveServiceOffering(entity);
    }

    @Test
    void saveServOffering_withInvalidDto_shouldReturn400() throws Exception {
        ServOfferingRequestDto dto = validDto();
        dto.setName("");

        ServiceOffering entity = new ServiceOffering();
        when(sMapper.toEntity(any(ServOfferingRequestDto.class))).thenReturn(entity);

        mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(sMapper).toEntity(any(ServOfferingRequestDto.class));
        verify(servOfferingService).saveServiceOffering(entity);
    }

    @Test
    void findAllServOffering_ok_shouldReturn200_andCallService() throws Exception {
        when(servOfferingService.findAllServOffering())
                .thenReturn(java.util.List.of(servicioConId(1L), servicioConId(2L)));

        mockMvc.perform(get("/api/service-offerings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(servOfferingService).findAllServOffering();
    }

    @Test
    void findServiceOffering_ok_shouldReturn200_andCallService() throws Exception{
        when (servOfferingService.findServiceOffering(1L)).thenReturn(servicioConId(1L));

        mockMvc.perform(get("/api/service-offerings/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Corte y barba"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.price").value(10000.0));
        verify(servOfferingService).findServiceOffering(1L);
    }

    @Test
    void findServOffering_withNonExistentId_shouldReturn404() throws Exception {
        when(servOfferingService.findServiceOffering(999L)).thenReturn(null);
        mockMvc.perform(get("/api/service-offerings/999"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(servOfferingService).findServiceOffering(999L);
    }

    @Test
    void updateServOffering_ok_shouldReturn200_andCallService() throws Exception{
        ServOfferingRequestDto dto = validDto();
        ServiceOffering entity = new ServiceOffering();
        when(sMapper.toEntity(any(ServOfferingRequestDto.class))).thenReturn(entity);
        mockMvc.perform(put("/api/service-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateServOffering_withInvalidDto_shouldReturn400() throws Exception{
        ServOfferingRequestDto dto = validDto();
        dto.setName("");

        ServiceOffering entity = new ServiceOffering();
        when(sMapper.toEntity(any(ServOfferingRequestDto.class))).thenReturn(entity);

        mockMvc.perform(put("/api/service-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(sMapper).toEntity(any(ServOfferingRequestDto.class));
        verify(servOfferingService).updateServOffering(entity, 1L);
    }

    @Test
    void deleteServOffering_ok_shouldReturn200_andCallService() throws Exception {
        mockMvc.perform(delete("/api/service-offerings/1"))
                .andExpect(status().isOk());

        verify(servOfferingService).deleteServOffering(1L);
    }
}
