package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
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

    private static final String BASE_URL = "/api/v1/service-offerings";

    private ServOfferingRequestDto getServiceOfferingDto(Long id) {
        return ServOfferingRequestDto.builder()
                .name("Corte y barba")
                .durationMinutes(60)
                .priceCents(10000)
                .build();
    }

    private ServiceOffering getServiceOfferingEntity(Long id) {
        return ServiceOffering.builder()
                .id(id)
                .name("Corte y barba")
                .durationMinutes(60)
                .priceCents(10000)
                .build();
    }

    private ServOfferingResponseDto getServiceOfferingResponseDto(Long id) {
        return ServOfferingResponseDto.builder()
                .id(id)
                .name("Corte y barba")
                .durationMinutes(60)
                .priceCents(10000)
                .build();
    }

    @Test
    void saveServOffering_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        Long id = 1L;
        var dto = getServiceOfferingDto(id);
        var entity = getServiceOfferingEntity(id);
        var responseDto = getServiceOfferingResponseDto(id);

        given(sMapper.toEntity(any(ServOfferingRequestDto.class))).willReturn(entity);
        given(servOfferingService.saveServiceOffering(any(ServiceOffering.class))).willReturn(entity);
        given(sMapper.toResponseDto(any(ServiceOffering.class))).willReturn(responseDto);

        // When
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Corte y barba"))
                .andExpect(jsonPath("$.duration_minutes").value(60))
                .andExpect(jsonPath("$.price_cents").value(10000));

        // Assert
        then(sMapper).should().toEntity(any(ServOfferingRequestDto.class));
        then(servOfferingService).should().saveServiceOffering(entity);
        then(sMapper).should().toResponseDto(entity);
    }

    @Test
    void saveServOffering_whenNameIsNull_shouldReturn400() throws Exception {
        //Given
        Long id = 1L;
        var dto = getServiceOfferingDto(id);
        dto.setName("");

        // When
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL))
                .andExpect(jsonPath("$.timestamp").exists());
        // Assert
        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void saveServOffering_whenDurationIsZero_shouldReturn400() throws Exception {
        var dto = getServiceOfferingDto(1L);
        dto.setDurationMinutes(0);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("durationMinutes"))
                .andExpect(jsonPath("$.details[0].message").exists());

        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void saveServOffering_whenPriceIsNegative_shouldReturn400() throws Exception {
        var dto = getServiceOfferingDto(1L);
        dto.setPriceCents(-1);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("priceCents"))
                .andExpect(jsonPath("$.details[0].message").exists());

        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void findAllServOffering_ok_shouldReturn200_andCallService() throws Exception {
        // Given
        Long id = 1L;
        var servOffering1 = getServiceOfferingEntity(id);
        var servOffering2 = getServiceOfferingEntity(2L);

        var response1 = getServiceOfferingResponseDto(id);
        var response2 = getServiceOfferingResponseDto(2L);

        given(servOfferingService.findAllServOffering()).willReturn(List.of(servOffering1, servOffering2));
        given(sMapper.toResponseDtoList(List.of(servOffering1, servOffering2))).willReturn(List.of(response1, response2));

        //when + then
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        then(servOfferingService).should().findAllServOffering();
        then(sMapper).should().toResponseDtoList(List.of(servOffering1, servOffering2));
    }

    @Test
    void findServiceOffering_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        Long id = 1L;
        var servOffering = getServiceOfferingEntity(id);
        var responseDto = getServiceOfferingResponseDto(id);

        given (servOfferingService.findServiceOffering(1L)).willReturn(servOffering);
        given(sMapper.toResponseDto(servOffering)).willReturn(responseDto);

        // When + Then
        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Corte y barba"))
                .andExpect(jsonPath("$.duration_minutes").value(60))
                .andExpect(jsonPath("$.price_cents").value(10000));

        then(servOfferingService).should().findServiceOffering(1L);
        then(sMapper).should().toResponseDto(servOffering);
    }

    @Test
    void findServOffering_withNonExistentId_shouldReturn404() throws Exception {
        //Given
        given(servOfferingService.findServiceOffering(999L))
                .willThrow(new ResourceNotFoundException("Service offering not found"));

        // When + Then
        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Service offering not found"));

        then(servOfferingService).should().findServiceOffering(999L);
        then(sMapper).shouldHaveNoInteractions();
    }

    @Test
    void updateServOffering_ok_shouldReturn200_andCallService() throws Exception{
        // Given
        Long id = 1L;

        var dto = ServOfferingUpdateRequestDto.builder()
                .name("Corte y barba")
                .durationMinutes(60)
                .priceCents(10000)
                .build();

        var entity = getServiceOfferingEntity(id);
        entity.setName("Corte y barba");
        entity.setDurationMinutes(60);
        entity.setPriceCents(10000);

        var responseDto = getServiceOfferingResponseDto(id);
        responseDto.setName("Corte y barba");
        responseDto.setDurationMinutes(60);
        responseDto.setPriceCents(10000);

        given(servOfferingService.updateServOffering(any(ServOfferingUpdateRequestDto.class), eq(1L))).willReturn(entity);
        given(sMapper.toResponseDto(entity)).willReturn(responseDto);

        // When + Then
        mockMvc.perform(patch(BASE_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Corte y barba"))
                .andExpect(jsonPath("$.duration_minutes").value(60))
                .andExpect(jsonPath("$.price_cents").value(10000));

        then(sMapper).should().toResponseDto(entity);
        then(servOfferingService).should().updateServOffering(any(ServOfferingUpdateRequestDto.class), eq(id));
    }

    @Test
    void updateServOffering_withInvalidDto_shouldReturn400() throws Exception{
        //Given
        var dto = ServOfferingUpdateRequestDto.builder()
                .name("")
                .build();

        // When + Then
        mockMvc.perform(patch(BASE_URL+ "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.path").value(BASE_URL + "/1"))
                .andExpect(jsonPath("$.timestamp").exists());

        then(sMapper).shouldHaveNoInteractions();
        then(servOfferingService).shouldHaveNoInteractions();
    }

    @Test
    void deleteServOffering_ok_shouldReturn204_andCallService() throws Exception {
        //When + Then
        mockMvc.perform(delete(BASE_URL + "/1"))
                .andExpect(status().isNoContent());

        then(servOfferingService).should().deleteServOffering(1L);
    }

    @Test
    void deleteServOffering_withNonExistentId_shouldReturn404() throws Exception {
        //Given
        willThrow(new ResourceNotFoundException("Service offering not found"))
                .given(servOfferingService).deleteServOffering(999L);

        // When + Then
        mockMvc.perform(delete(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Service offering not found"));

        then(servOfferingService).should().deleteServOffering(999L);
    }
}


