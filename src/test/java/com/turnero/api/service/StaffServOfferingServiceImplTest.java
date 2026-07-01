package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.StaffServiceOffering;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffServiceOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaffServOfferingServiceImplTest {

    @InjectMocks
    private StaffServOfferingServiceImpl staffServOfferingService;

    @Mock
    private StaffServiceOfferingRepository staffServiceOfferingRepository;

    @Mock
    private StaffMemberRepository staffMemberRepository;

    @Mock
    private ServOfferingRepository servOfferingRepository;

    @Mock
    private CurrentBusinessContext currentBusinessContext;

    @Mock
    private ServiceOfferingMapper serviceOfferingMapper;

    @Test
    void getServiceOfferings_whenStaffExists_returnsAssociatedServices() {
        Long businessId = 1L;
        Long staffMemberId = 10L;
        Long serviceOfferingId = 20L;

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .name("Haircut")
                .category("Hair")
                .durationMinutes(30)
                .priceCents(2500)
                .status(ServiceOfferingStatus.ACTIVE)
                .build();

        ServOfferingResponseDto responseDto = ServOfferingResponseDto.builder()
                .id(serviceOfferingId)
                .name("Haircut")
                .category("Hair")
                .durationMinutes(30)
                .priceCents(2500)
                .status(ServiceOfferingStatus.ACTIVE)
                .build();

        StaffServiceOffering association = StaffServiceOffering.builder()
                .staffMemberId(staffMemberId)
                .serviceOfferingId(serviceOfferingId)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(association));
        when(servOfferingRepository.findAllByIdInAndBusinessId(List.of(serviceOfferingId), businessId))
                .thenReturn(List.of(serviceOffering));
        when(serviceOfferingMapper.toResponseDto(serviceOffering)).thenReturn(responseDto);

        List<ServOfferingResponseDto> result = staffServOfferingService.getServiceOfferings(staffMemberId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(serviceOfferingId);
        assertThat(result.get(0).getName()).isEqualTo("Haircut");
    }

    @Test
    void replacesServiceOfferings_replacesAssociations() {
        Long businessId = 1L;
        Long staffMemberId = 10L;
        List<Long> serviceOfferingIds = List.of(20L, 30L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(servOfferingRepository.findAllByIdInAndBusinessId(serviceOfferingIds, businessId))
                .thenReturn(List.of(
                        ServiceOffering.builder().id(20L).businessId(businessId).status(ServiceOfferingStatus.ACTIVE).build(),
                        ServiceOffering.builder().id(30L).businessId(businessId).status(ServiceOfferingStatus.ACTIVE).build()
                ));

        staffServOfferingService.replaceServiceOfferings(staffMemberId, serviceOfferingIds);
        verify(staffServiceOfferingRepository).deleteAllByStaffMemberId(staffMemberId);
        verify(staffServiceOfferingRepository).saveAll(anyList());
    }

    @Test
    void replacesServiceOfferings_rejectsStaffFromOtherBusiness() {
        Long businessId = 1L;
        Long staffMemberId = 10L;
        List<Long> serviceOfferingIds = List.of(20L, 30L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> staffServOfferingService.replaceServiceOfferings
                (10L, List.of(1L))
        );

        verifyNoInteractions(servOfferingRepository);
    }

    @Test
    void replaceServiceOfferings_rejectsServicesFromOtherBusiness() {
        Long businessId = 1L;
        Long staffMemberId = 10L;
        List<Long> serviceOfferingIds = List.of(20L, 30L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(servOfferingRepository.findAllByIdInAndBusinessId(serviceOfferingIds, businessId))
                .thenReturn(List.of(
                        ServiceOffering.builder()
                                        .id(20L)
                                .businessId(businessId)
                                .status(ServiceOfferingStatus.ACTIVE)
                                .build()
                ));

        assertThrows(ResourceNotFoundException.class, () -> staffServOfferingService.replaceServiceOfferings
                (staffMemberId, serviceOfferingIds));

        verify(staffServiceOfferingRepository, never()).deleteAllByStaffMemberId(anyLong());
        verify(staffServiceOfferingRepository, never()).saveAll(anyList());
    }

    @Test
    void replaceServiceOfferings_rejectsInactiveServices() {
        Long businessId = 1L;
        Long staffMemberId = 10L;
        List<Long> serviceOfferingIds = List.of(20L, 30L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(servOfferingRepository.findAllByIdInAndBusinessId(serviceOfferingIds, businessId))
                .thenReturn(List.of(
                        ServiceOffering.builder()
                                .id(20L)
                                .businessId(businessId)
                                .status(ServiceOfferingStatus.ACTIVE)
                                .build(),
                        ServiceOffering.builder()
                                .id(30L)
                                .businessId(businessId)
                                .status(ServiceOfferingStatus.INACTIVE)
                                .build()
                ));

        assertThrows(AppointmentOverlapException.class, () ->
                staffServOfferingService.replaceServiceOfferings(staffMemberId, serviceOfferingIds)
        );

        verify(staffServiceOfferingRepository, never()).deleteAllByStaffMemberId(anyLong());
        verify(staffServiceOfferingRepository, never()).saveAll(anyList());
    }

    @Test
    void replacesServiceOfferings_removesDuplicates() {
        Long businessId = 1L;
        Long staffMemberId = 10L;
        List<Long> serviceOfferingIds = List.of(20L, 20L, 30L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(servOfferingRepository.findAllByIdInAndBusinessId(List.of(20L, 30L), businessId))
                .thenReturn(List.of(
                        ServiceOffering.builder().id(20L).businessId(businessId).status(ServiceOfferingStatus.ACTIVE).build(),
                        ServiceOffering.builder().id(30L).businessId(businessId).status(ServiceOfferingStatus.ACTIVE).build()
                ));

        staffServOfferingService.replaceServiceOfferings(staffMemberId, serviceOfferingIds);
        verify(staffServiceOfferingRepository).deleteAllByStaffMemberId(staffMemberId);
        ArgumentCaptor<List<StaffServiceOffering>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(staffServiceOfferingRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(2);
    }
}
