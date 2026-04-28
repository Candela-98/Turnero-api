package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.service.ServOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

    private ServOfferingRequestDto getServiceOfferingDto(Long id) {
        return ServOfferingRequestDto.builder()
                .Id(id)
                .name("Corte y barba")
                .durationMinutes(60)
                .price(10000.0)
                .build();
    }

    private ServiceOffering getServiceOfferingEntity(Long id) {
        return ServiceOffering.builder()
                .id(id)
                .name("Corte y barba")
                .durationMinutes(60)
                .price(10000.0)
                .build();
    }

    @Test
    void saveServOffering_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        Long id = 1L;
        var dto = getServiceOfferingDto(id);
        var entity = getServiceOfferingEntity(id);
        given(sMapper.toEntity(any(ServOfferingRequestDto.class))).willReturn(entity);

        // When
        mockMvc.perform(post("/api/service-offerings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Assert
        then(sMapper).should().toEntity(any(ServOfferingRequestDto.class));
        then(servOfferingService).should().saveServiceOffering(entity);
    }

    @Test
    void saveServOffering_whenNameIsNull_shouldReturn400() throws Exception {
        //Given
        Long id = 1L;
        var dto = getServiceOfferingDto(id);
        dto.setName(null);

        // When
        mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.name").exists());

        // Assert
        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void findAllServOffering_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        Long id = 1L;
        var servOffering1 = getServiceOfferingEntity(id);
        var servOffering2 = getServiceOfferingEntity(2L);

        given(servOfferingService.findAllServOffering())
                .willReturn(List.of(servOffering1, servOffering2));

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
        Long id = 1L;
        var servOffering = getServiceOfferingEntity(id);
        given (servOfferingService.findServiceOffering(1L)).willReturn(servOffering);

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
        given(servOfferingService.findServiceOffering(999L))
                .willThrow(new ResourceNotFoundException("Service offering not found"));

        // When + Then
        mockMvc.perform(get("/api/service-offerings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Service offering not found"));

        then(servOfferingService).should().findServiceOffering(999L);
    }

    @Test
    void updateServOffering_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        Long id = 1L;
        var dto = getServiceOfferingDto(id);
        ServiceOffering entity = getServiceOfferingEntity(id);
        given(sMapper.toEntity(any(ServOfferingRequestDto.class))).willReturn(entity);

        // When + Then
        mockMvc.perform(put("/api/service-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        then(sMapper).should().toEntity(any(ServOfferingRequestDto.class));
        then(servOfferingService).should().updateServOffering(entity, 1L);
    }

    @Test
    void updateServOffering_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        Long id = 1L;
        var dto = getServiceOfferingDto(id);
        dto.setName(null);

        // When + Then
        mockMvc.perform(put("/api/service-offerings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.validations.name").exists());

        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void deleteServOffering_ok_shouldReturn200_andCallService() throws Exception {
        //When + Then
        mockMvc.perform(delete("/api/service-offerings/1"))
                .andExpect(status().isNoContent());

        then(servOfferingService).should().deleteServOffering(1L);
    }

    @Test
    void deleteServOffering_withNonExistentId_shouldReturn404() throws Exception {
        //Given
        willThrow(new ResourceNotFoundException("Service offering not found"))
                .given(servOfferingService).deleteServOffering(999L);

        // When + Then
        mockMvc.perform(delete("/api/service-offerings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Service offering not found"));

        then(servOfferingService).should().deleteServOffering(999L);
    }
}
