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
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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

    private ServiceOffering getServiceOfferingEntity() {
        ServiceOffering s = new ServiceOffering();
        s.setId(1L);
        s.setName("Corte y barba");
        s.setDurationMinutes(60);
        s.setPrice(10000.0);
        return s;
    }

    private ServiceOffering servOfferingWithId(long id){
        ServiceOffering s = new ServiceOffering();
        s.setId(id);
        s.setName("Corte y barba");
        s.setDurationMinutes(60);
        s.setPrice(10000.0);
        return s;
    }

    @Test
    void saveServOffering_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        ServOfferingRequestDto dto = validDto();
        ServiceOffering entity = new ServiceOffering();
        given(sMapper.toEntity(any(ServOfferingRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(post("/api/service-offerings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // Assert
        then(sMapper).should().toEntity(any(ServOfferingRequestDto.class));
        then(servOfferingService).should().saveServiceOffering(entity);
    }

    @Test
    void saveServOffering_withInvalidDto_shouldReturn400() throws Exception {
        //Given
        ServOfferingRequestDto dto = validDto();
        dto.setName("");
        ServiceOffering entity = new ServiceOffering();
        given(sMapper.toEntity(any(ServOfferingRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // Assert
        then(sMapper).should().toEntity(any(ServOfferingRequestDto.class));
        then(servOfferingService).should().saveServiceOffering(entity);
    }

    @Test
    void findAllServOffering_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        given(servOfferingService.findAllServOffering())
                .willReturn(java.util.List.of(servOfferingWithId(1L), servOfferingWithId(2L)));

        //when + then
        mockMvc.perform(get("/api/service-offerings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        then(servOfferingService).should().findAllServOffering();
    }

    @Test
    void findServiceOffering_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        given (servOfferingService.findServiceOffering(1L)).willReturn(servOfferingWithId(1L));

        // When + Then
        mockMvc.perform(get("/api/service-offerings/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Corte y barba"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.price").value(10000.0));
        then(servOfferingService).should().findServiceOffering(1L);
    }

    @Test
    void findServOffering_withNonExistentId_shouldReturn404() throws Exception {
        //Given
        given(servOfferingService.findServiceOffering(999L)).willThrow(new ResponseStatusException(NOT_FOUND, "Service offering not found"));

        // When + Then
        mockMvc.perform(get("/api/service-offerings/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        then(servOfferingService).should().findServiceOffering(999L);
    }

    @Test
    void updateServOffering_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        ServOfferingRequestDto dto = validDto();
        ServiceOffering entity = new ServiceOffering();
        given(sMapper.toEntity(any(ServOfferingRequestDto.class))).willReturn(entity);

        // When + Then
        mockMvc.perform(put("/api/service-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        then(sMapper).should().toEntity(any(ServOfferingRequestDto.class));
        then(servOfferingService).should().updateServOffering(entity, 1L);
    }

    @Test
    void updateServOffering_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        ServOfferingRequestDto dto = validDto();
        dto.setName(null);

        // When + Then
        mockMvc.perform(put("/api/service-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void deleteServOffering_ok_shouldReturn200_andCallService() throws Exception {
        //When + Then
        mockMvc.perform(delete("/api/service-offerings/1"))
                .andExpect(status().isOk());

        then(servOfferingService).should().deleteServOffering(1L);
    }
}
