package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.dto.ServOfferingRequestDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .name("Haircut and Beard")
                .durationMinutes(60)
                .price(10000.0)
                .build();
    }

    private ServiceOffering getServiceOffering() {
        return ServiceOffering.builder()
                .name("Haircut and Beard")
                .durationMinutes(60)
                .price(10000.0)
                .build();
    }


    @Test
    void saveServiceOffering_whenRequestIsValid_persistsServiceOffering_andReturns200() throws Exception {
        // Given
        ServOfferingRequestDto dto = getServOfferingRequestDto();

        // When
        mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        //Then
        List<ServiceOffering> serviceOfferings = servOfferingRepository.findAll();
        assertThat(serviceOfferings).hasSize(1);
        ServiceOffering saved = serviceOfferings.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(dto.getName());
        assertThat(saved.getDurationMinutes()).isEqualTo(dto.getDurationMinutes());
        assertThat(saved.getPrice()).isEqualTo(dto.getPrice());
    }

    @Test
    void saveServiceOffering_whenNameIsNull_returns400() throws Exception {
        //Given
        ServOfferingRequestDto dto = getServOfferingRequestDto();
        dto.setName(null);

        // When + Then
        mockMvc.perform(post("/api/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

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
        ServiceOffering response = objectMapper.readValue(json, ServiceOffering.class);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(saved.getName()).isEqualTo("Haircut and Beard");
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getPrice()).isEqualTo(10000.0);

    }

    @Test
    void findServiceOffering_whenServiceOfferingDoesNotExist_returns404() throws Exception {
        Long id = 999L;

        mockMvc.perform(get("/api/service-offerings/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateServiceOffering_whenRequestIsValid_updatesServiceOffering_andReturns204() throws Exception {
        //Given
        ServiceOffering saved = servOfferingRepository.save(getServiceOffering());

        ServOfferingRequestDto dto = getServOfferingRequestDto();
        dto.setName("Haircut");
        dto.setDurationMinutes(45);
        dto.setPrice(8000.0);

        // When
        mockMvc.perform(put("/api/service-offerings/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        // Then
        ServiceOffering updated = servOfferingRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Haircut");
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
                .andExpect(status().isBadRequest());

    }

    @Test
    void listServiceOffering_whenServiceOfferingsExist_returns200AndServiceOfferingList() throws Exception {
        //Given
        ServiceOffering serviceOffering1 = getServiceOffering();
        ServiceOffering serviceOffering2 = new ServiceOffering();
        serviceOffering2.setName("Hair Coloring");
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
        List<ServiceOffering> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(2);
        assertThat(response).extracting(ServiceOffering::getName).containsExactlyInAnyOrder("Haircut and Beard", "Hair Coloring");
        assertThat(response).extracting(ServiceOffering::getDurationMinutes).containsExactlyInAnyOrder(60, 90);
        assertThat(response).extracting(ServiceOffering::getPrice).containsExactlyInAnyOrder(10000.0, 15000.0);

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
        List<ServiceOffering> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).isEmpty();
    }

    @Test
    void  deleteServiceOffering_whenServiceOfferingExists_deletesServiceOffering_andReturns204() throws Exception {
        //Given
        ServiceOffering serviceOffering = getServiceOffering();
        ServiceOffering saved = servOfferingRepository.save(serviceOffering);
        Long id = saved.getId();

        //When
        mockMvc.perform(delete("/api/service-offerings/{id}", id))
                .andExpect(status().isNoContent());

        //Then
        assertThat(servOfferingRepository.existsById(id)).isFalse();
    }

    @Test
    void deleteServiceOffering_whenServiceOfferingDoesNotExist_returns404() throws Exception {
        // Given
        Long id = 999L;

        // When + Then
        mockMvc.perform(delete("/api/service-offerings/{id}", id))
                .andExpect(status().isNotFound());
    }

}
