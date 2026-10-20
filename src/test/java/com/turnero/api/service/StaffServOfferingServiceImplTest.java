package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingResponseDto;
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

    private void staffExists() {
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        when(staffMemberRepository.findByIdAndBusinessIdForUpdate(10L, 1L))
                .thenReturn(java.util.Optional.of(new com.turnero.api.model.StaffMember()));
    }
    private ServiceOffering offering(long id, ServiceOfferingStatus status) {
        return ServiceOffering.builder().id(id).businessId(1L).status(status).build();
    }
    private StaffServiceOffering association(long id) {
        return StaffServiceOffering.builder().id(id).staffMemberId(10L).serviceOfferingId(id).build();
    }
    @Test
    void replacesByDifferenceRetainingExistingInactiveRowsAndNormalizingIds() {
        staffExists();
        var retained = association(20L);
        var removed = association(40L);
        when(staffServiceOfferingRepository.findAllByStaffMemberId(10L)).thenReturn(List.of(retained, removed));
        when(servOfferingRepository.findAllForUpdate(List.of(20L, 30L), 1L)).thenReturn(List.of(
                offering(20L, ServiceOfferingStatus.INACTIVE), offering(30L, ServiceOfferingStatus.ACTIVE)));
        staffServOfferingService.replaceServiceOfferings(10L, List.of(30L, 20L, 30L));
        verify(staffServiceOfferingRepository).deleteAll(List.of(removed));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffServiceOffering>> added = ArgumentCaptor.forClass(List.class);
        verify(staffServiceOfferingRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(StaffServiceOffering::getServiceOfferingId).containsExactly(30L);
        verify(staffServiceOfferingRepository, never()).deleteAllByStaffMemberId(anyLong());
    }
    @Test
    void rejectsForeignOrMissingStaffBeforeReadingServices() {
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(1L);
        assertThrows(ResourceNotFoundException.class, () -> staffServOfferingService.replaceServiceOfferings(10L, List.of(1L)));
        verifyNoInteractions(servOfferingRepository, staffServiceOfferingRepository);
    }
    @Test
    void rejectsMissingForeignOrRemovedServicesBeforeMutating() {
        staffExists();
        when(servOfferingRepository.findAllForUpdate(List.of(20L, 30L), 1L))
                .thenReturn(List.of(offering(20L, ServiceOfferingStatus.ACTIVE)));
        assertThrows(ResourceNotFoundException.class, () -> staffServOfferingService.replaceServiceOfferings(10L, List.of(20L, 30L)));
        verify(staffServiceOfferingRepository, never()).deleteAll(anyList());
        verify(staffServiceOfferingRepository, never()).saveAll(anyList());
    }
    @Test
    void rejectsNewInactiveAssociation() {
        staffExists();
        when(servOfferingRepository.findAllForUpdate(List.of(30L), 1L))
                .thenReturn(List.of(offering(30L, ServiceOfferingStatus.INACTIVE)));
        assertThrows(com.turnero.api.exception.InvalidStateTransitionException.class,
                () -> staffServOfferingService.replaceServiceOfferings(10L, List.of(30L)));
        verify(staffServiceOfferingRepository, never()).deleteAll(anyList());
        verify(staffServiceOfferingRepository, never()).saveAll(anyList());
    }
    @Test
    void emptySelectionRemovesAllIncludingInactiveWithoutLoadingCatalog() {
        staffExists();
        var old = association(20L);
        when(staffServiceOfferingRepository.findAllByStaffMemberId(10L)).thenReturn(List.of(old));
        staffServOfferingService.replaceServiceOfferings(10L, List.of());
        verify(staffServiceOfferingRepository).deleteAll(List.of(old));
        verify(staffServiceOfferingRepository, never()).saveAll(anyList());
        verifyNoInteractions(servOfferingRepository);
    }
    @Test
    void unchangedSelectionDoesNotRewriteRows() {
        staffExists();
        when(staffServiceOfferingRepository.findAllByStaffMemberId(10L)).thenReturn(List.of(association(20L)));
        when(servOfferingRepository.findAllForUpdate(List.of(20L), 1L)).thenReturn(List.of(offering(20L, ServiceOfferingStatus.INACTIVE)));
        staffServOfferingService.replaceServiceOfferings(10L, List.of(20L));
        verify(staffServiceOfferingRepository, never()).deleteAll(anyList());
        verify(staffServiceOfferingRepository, never()).saveAll(anyList());
    }
    @Test
    void rejectsInvalidIdsEvenForInternalCalls() {
        for (List<Long> ids : java.util.Arrays.asList(null, java.util.Arrays.asList((Long) null), List.of(0L), List.of(-1L))) {
            assertThrows(IllegalArgumentException.class, () -> staffServOfferingService.replaceServiceOfferings(10L, ids));
        }
        verifyNoInteractions(staffMemberRepository, staffServiceOfferingRepository, servOfferingRepository);
    }
}
