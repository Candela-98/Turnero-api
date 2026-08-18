package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.BusinessUpdateRequestDto;
import com.turnero.api.model.Business;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.repository.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
        request.setTimezone("UTC");
        given(currentBusinessContext.getCurrentBusinessId()).willReturn(1L);
        given(businessRepository.findById(1L)).willReturn(Optional.of(business));
        given(businessRepository.save(business)).willReturn(business);

        businessService.updateCurrentBusiness(request);

        assertThat(business.getName()).isEqualTo("Barber Studio Palermo");
        assertThat(business.getTimezone()).isEqualTo("UTC");
        assertThat(business.getSlug()).isEqualTo("barber-studio");
        assertThat(business.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(business.getOnboardingStatus()).isEqualTo(BusinessOnboardingStatus.PENDING_SETUP);
        assertThat(business.getUpdatedAt()).isNotNull();
        verify(businessRepository).save(business);
    }

    private Business business() {
        return Business.builder().id(1L).name("Barber Studio").slug("barber-studio")
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP).build();
    }
}
