package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ServOfferingRequestDto;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.service.ServOfferingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ServOfferingControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ServiceOfferingMapper serviceOfferingMapper;

    @Autowired
    ServOfferingService servOfferingService;

    @Autowired
    ServOfferingRepository servOfferingRepository;

    @BeforeEach
    void cleanDb() {
        servOfferingRepository.deleteAll();
    }

    private ServOfferingRequestDto getServOfferingRequestDto() {
        return ServOfferingRequestDto.builder()
                .name("Corte y barba")
                .durationMinutes(60)
                .price(10000.0)
                .build();
    }

    private ServiceOffering getServiceOffering() {
        return ServiceOffering.builder()
                .name("Corte y barba")
                .durationMinutes(60)
                .price(10000.0)
                .build();
    }

    @Test
    void saveServiceOffering_whenRequestIsValid_persistsServiceOffering_andReturns200() throws Exception {
        // Given
        ServOfferingRequestDto dto = getServOfferingRequestDto();

        // When
        MvcResult result = mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        //Then
        List<ServiceOffering> serviceOfferings = servOfferingRepository.findAll();
        assertThat(serviceOfferings).hasSize(1);

        ServiceOffering saved = serviceOfferings.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(dto.getName());
        assertThat(saved.getDurationMinutes()).isEqualTo(dto.getDurationMinutes());
        assertThat(saved.getPrice()).isEqualTo(dto.getPrice());

        String json = result.getResponse().getContentAsString();
        ServOfferingResponseDto response = objectMapper.readValue(json, ServOfferingResponseDto.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getName()).isEqualTo(dto.getName());
        assertThat(response.getDurationMinutes()).isEqualTo(dto.getDurationMinutes());
        assertThat(response.getPrice()).isEqualTo(dto.getPrice());
    }

    @Test
    void saveServiceOffering_whenNameIsNull_returns400() throws Exception {
        //Given
        ServOfferingRequestDto dto = getServOfferingRequestDto();
        dto.setName("");

        // When + Then
        mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.error").value("Bad Request"))
                        .andExpect(jsonPath("$.validations.name").value("The service offering name is required"));

        assertThat(servOfferingRepository.findAll()).isEmpty();
    }

    @Test
    void findServiceOffering_whenServiceOfferingExists_returns200AndServiceOffering() throws Exception {
        //Given
        ServiceOffering saved = servOfferingRepository.save(getServiceOffering());

        // When
        MvcResult result = mockMvc.perform(get("/api/service-offerings/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        ServOfferingResponseDto response = objectMapper.readValue(json, ServOfferingResponseDto.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(saved.getName()).isEqualTo("Corte y barba");
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getPrice()).isEqualTo(10000.0);

    }

    @Test
    void findServiceOffering_whenServiceOfferingDoesNotExist_returns404() throws Exception {
        Long id = 999L;

        mockMvc.perform(get("/api/service-offerings/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Service offering not found with ID: " + id));
    }

    @Test
    void updateServiceOffering_whenRequestIsValid_updatesServiceOffering_andReturns204() throws Exception {
        //Given
        ServiceOffering saved = servOfferingRepository.save(getServiceOffering());

        ServOfferingRequestDto dto = getServOfferingRequestDto();
        dto.setName("Corte");
        dto.setDurationMinutes(45);
        dto.setPrice(8000.0);

        // When
        mockMvc.perform(put("/api/service-offerings/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        ServiceOffering updated = servOfferingRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Corte");
        assertThat(updated.getDurationMinutes()).isEqualTo(45);
        assertThat(updated.getPrice()).isEqualTo(8000.0);
    }

    @Test
    void updateServiceOffering_whenNameIsNull_returns400() throws Exception{
        //Given
        ServOfferingRequestDto dto = getServOfferingRequestDto();
        dto.setName(null);

        // When + Then
        mockMvc.perform(put("/api/service-offerings/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validations.name").value("The service offering name is required"));

    }

    @Test
    void listServiceOffering_whenServiceOfferingsExist_returns200AndServiceOfferingList() throws Exception {
        //Given
        ServiceOffering serviceOffering1 = getServiceOffering();
        ServiceOffering serviceOffering2 = new ServiceOffering();
        serviceOffering2.setName("Coloración");
        serviceOffering2.setDurationMinutes(90);
        serviceOffering2.setPrice(15000.0);

        servOfferingRepository.save(serviceOffering1);
        servOfferingRepository.save(serviceOffering2);

        // When
        MvcResult result = mockMvc.perform(get("/api/service-offerings")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<ServOfferingResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(2);
        assertThat(response).extracting(ServOfferingResponseDto::getName).containsExactlyInAnyOrder("Corte y barba", "Coloración");
        assertThat(response).extracting(ServOfferingResponseDto::getDurationMinutes).containsExactlyInAnyOrder(60, 90);
        assertThat(response).extracting(ServOfferingResponseDto::getPrice).containsExactlyInAnyOrder(10000.0, 15000.0);

    }

    @Test
    void listServiceOffering_whenNoServiceOfferingsExist_returns200AndEmptyList() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/service-offerings")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<ServOfferingResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).isEmpty();
    }

    @Test
    void  deleteServiceOffering_whenServiceOfferingExists_deletesServiceOffering_andReturns204() throws Exception {
        //Given
        ServiceOffering serviceOffering = getServiceOffering();
        ServiceOffering saved = servOfferingRepository.save(serviceOffering);
        Long id = saved.getId();

        //When
        MvcResult result = mockMvc.perform(delete("/api/service-offerings/{id}", id))
                .andExpect(status().isNoContent())
                .andReturn();

        //Then
        assertThat(servOfferingRepository.existsById(id)).isFalse();
    }

    @Test
    void deleteServiceOffering_whenServiceOfferingDoesNotExist_returns404() throws Exception {
        // Given
        Long id = 999L;

        // When + Then
        mockMvc.perform(delete("/api/service-offerings/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Service offering not found with ID: " + id));
    }

}
