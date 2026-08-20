package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BusinessUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Business;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.repository.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceImplTest {
    @Mock private BusinessRepository businessRepository;
    @Mock private CurrentBusinessContext currentBusinessContext;
    @InjectMocks private BusinessServiceImpl businessService;

    @Test
    void getCurrentBusiness_usesBusinessFromContext() {
        Business business = business();
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessRepository.findById(1L)).willReturn(Optional.of(business));

        assertThat(businessService.getCurrentBusiness()).isSameAs(business);
        verify(businessRepository).findById(1L);
    }

    @Test
    void updateCurrentBusiness_updatesOnlyAllowedFields() {
        Business business = business();
        BusinessUpdateRequestDto request = new BusinessUpdateRequestDto();
        request.setName("Barber Studio Palermo");
        request.setIndustry("Barber shop");
        request.setEmail("contact@barber-studio.com");
        request.setPhone("+54 11 5555 5555");
        request.setAddress("Av. Siempre Viva 123");
        request.setTimezone("UTC");
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessRepository.findById(1L)).willReturn(Optional.of(business));
        given(businessRepository.save(business)).willReturn(business);

        businessService.updateCurrentBusiness(request);

        assertThat(business.getName()).isEqualTo("Barber Studio Palermo");
        assertThat(business.getIndustry()).isEqualTo("Barber shop");
        assertThat(business.getEmail()).isEqualTo("contact@barber-studio.com");
        assertThat(business.getPhone()).isEqualTo("+54 11 5555 5555");
        assertThat(business.getAddress()).isEqualTo("Av. Siempre Viva 123");
        assertThat(business.getTimezone()).isEqualTo("UTC");
        assertThat(business.getSlug()).isEqualTo("barber-studio");
        assertThat(business.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(business.getOnboardingStatus()).isEqualTo(BusinessOnboardingStatus.PENDING_SETUP);
        assertThat(business.getUpdatedAt()).isNotNull();
        verify(businessRepository).save(business);
    }

    @Test
    void updateCurrentBusiness_whenTimezoneIsInvalid_rejectsRequestWithoutSaving() {
        BusinessUpdateRequestDto request = new BusinessUpdateRequestDto();
        request.setTimezone("not-a-timezone");
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessRepository.findById(1L)).willReturn(Optional.of(business()));

        assertThatThrownBy(() -> businessService.updateCurrentBusiness(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Timezone must be a valid IANA timezone");

        verify(businessRepository, never()).save(any());
    }

    @Test
    void getCurrentBusiness_whenBusinessDoesNotExist_throwsNotFound() {
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> businessService.getCurrentBusiness())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Business not found");
    }

    private Business business() {
        return Business.builder().id(1L).name("Barber Studio").slug("barber-studio")
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP).build();
    }
}
