package com.turnero.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.dto.PublicCancellationAppointmentDto;
import com.turnero.api.dto.PublicCancellationBusinessDto;
import com.turnero.api.dto.PublicCancellationRequestDto;
import com.turnero.api.dto.PublicCancellationResponseDto;
import com.turnero.api.dto.PublicCancellationResultResponseDto;
import com.turnero.api.exception.InvalidPublicTokenException;
import com.turnero.api.exception.InvalidStateTransitionException;
import com.turnero.api.exception.PublicTokenExpiredException;
import com.turnero.api.exception.PublicTokenUsedException;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.service.PublicCancellationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCancellationController.class)
class PublicCancellationControllerTest {

    private static final String TOKEN = "plain-cancel-token";
    private static final String CANCELLATION_URL = "/api/v1/public/cancellations/{token}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PublicCancellationService publicCancellationService;
    @MockitoBean private AdminAuthInterceptor adminAuthInterceptor;

    @Test
    void getPublicCancellation_whenTokenIsValid_returnsPublicCancellationContract() throws Exception {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 15, 10, 0);
        LocalDateTime endsAt = startsAt.plusMinutes(45);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        given(publicCancellationService.getPublicCancellation(TOKEN))
                .willReturn(publicCancellationResponse(startsAt, endsAt, AppointmentStatus.CONFIRMED, true));

        mockMvc.perform(get(CANCELLATION_URL, TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.appointment.service_name").value("Corte + barba"))
                .andExpect(jsonPath("$.appointment.staff_member_name").value("Mateo Ruiz"))
                .andExpect(jsonPath("$.appointment.starts_at").value(startsAt.format(formatter)))
                .andExpect(jsonPath("$.appointment.ends_at").value(endsAt.format(formatter)))
                .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.business.name").value("Barber Studio"))
                .andExpect(jsonPath("$.business.timezone").value("America/Argentina/Buenos_Aires"))
                .andExpect(jsonPath("$.can_cancel").value(true))
                .andExpect(jsonPath("$.appointment.id").doesNotExist())
                .andExpect(jsonPath("$.appointment.appointment_id").doesNotExist())
                .andExpect(jsonPath("$.appointment.service_offering_id").doesNotExist())
                .andExpect(jsonPath("$.appointment.staff_member_id").doesNotExist())
                .andExpect(jsonPath("$.business.id").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.token_hash").doesNotExist());

        then(publicCancellationService).should().getPublicCancellation(TOKEN);
        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getPublicCancellation_whenTokenIsInvalid_returnsTokenInvalid() throws Exception {
        given(publicCancellationService.getPublicCancellation(TOKEN))
                .willThrow(new InvalidPublicTokenException("Invalid public cancellation token"));

        mockMvc.perform(get(CANCELLATION_URL, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getPublicCancellation_whenTokenWasUsed_returnsTokenUsed() throws Exception {
        given(publicCancellationService.getPublicCancellation(TOKEN))
                .willThrow(new PublicTokenUsedException("This cancellation token has already been used"));

        mockMvc.perform(get(CANCELLATION_URL, TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TOKEN_USED"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getPublicCancellation_whenTokenIsExpired_returnsTokenExpired() throws Exception {
        given(publicCancellationService.getPublicCancellation(TOKEN))
                .willThrow(new PublicTokenExpiredException("Public cancellation token has expired"));

        mockMvc.perform(get(CANCELLATION_URL, TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getPublicCancellation_whenAppointmentStateIsInvalid_returnsInvalidStateTransition() throws Exception {
        given(publicCancellationService.getPublicCancellation(TOKEN))
                .willThrow(new InvalidStateTransitionException("Appointment cannot be cancelled from status CANCELLED"));

        mockMvc.perform(get(CANCELLATION_URL, TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void cancelPublicAppointment_whenRequestIsValid_returnsResultContractAndDelegatesToService() throws Exception {
        given(publicCancellationService.cancelPublicAppointment(eq(TOKEN), any(PublicCancellationRequestDto.class)))
                .willReturn(PublicCancellationResultResponseDto.builder()
                        .status(AppointmentStatus.CANCELLED)
                        .message("Tu turno fue cancelado")
                        .build());

        mockMvc.perform(post(CANCELLATION_URL, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancellation_reason": "No puedo asistir"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value("Tu turno fue cancelado"))
                .andExpect(jsonPath("$.cancellation_reason").doesNotExist())
                .andExpect(jsonPath("$.cancellationReason").doesNotExist());

        ArgumentCaptor<PublicCancellationRequestDto> requestCaptor =
                ArgumentCaptor.forClass(PublicCancellationRequestDto.class);
        then(publicCancellationService).should().cancelPublicAppointment(eq(TOKEN), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCancellationReason()).isEqualTo("No puedo asistir");
        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void cancelPublicAppointment_whenNoAdminCookieIsPresent_stillAllowsPublicEndpoint() throws Exception {
        PublicCancellationRequestDto request = new PublicCancellationRequestDto();
        request.setCancellationReason("No puedo asistir");

        given(publicCancellationService.cancelPublicAppointment(eq(TOKEN), any(PublicCancellationRequestDto.class)))
                .willReturn(PublicCancellationResultResponseDto.builder()
                        .status(AppointmentStatus.CANCELLED)
                        .message("Tu turno fue cancelado")
                        .build());

        mockMvc.perform(post(CANCELLATION_URL, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void cancelPublicAppointment_whenTokenIsInvalid_returnsTokenInvalid() throws Exception {
        given(publicCancellationService.cancelPublicAppointment(eq(TOKEN), any(PublicCancellationRequestDto.class)))
                .willThrow(new InvalidPublicTokenException("Invalid public cancellation token"));

        mockMvc.perform(post(CANCELLATION_URL, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancellation_reason": "No puedo asistir"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void cancelPublicAppointment_whenTokenWasUsed_returnsTokenUsed() throws Exception {
        given(publicCancellationService.cancelPublicAppointment(eq(TOKEN), any(PublicCancellationRequestDto.class)))
                .willThrow(new PublicTokenUsedException("This cancellation token has already been used"));

        mockMvc.perform(post(CANCELLATION_URL, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancellation_reason": "No puedo asistir"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TOKEN_USED"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void cancelPublicAppointment_whenTokenIsExpired_returnsTokenExpired() throws Exception {
        given(publicCancellationService.cancelPublicAppointment(eq(TOKEN), any(PublicCancellationRequestDto.class)))
                .willThrow(new PublicTokenExpiredException("Public cancellation token has expired"));

        mockMvc.perform(post(CANCELLATION_URL, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancellation_reason": "No puedo asistir"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void cancelPublicAppointment_whenAppointmentStateIsInvalid_returnsInvalidStateTransition() throws Exception {
        given(publicCancellationService.cancelPublicAppointment(eq(TOKEN), any(PublicCancellationRequestDto.class)))
                .willThrow(new InvalidStateTransitionException("Appointment can no longer be cancelled"));

        mockMvc.perform(post(CANCELLATION_URL, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancellation_reason": "No puedo asistir"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    private PublicCancellationResponseDto publicCancellationResponse(LocalDateTime startsAt, LocalDateTime endsAt,
            AppointmentStatus status, boolean canCancel) {
        return PublicCancellationResponseDto.builder()
                .appointment(PublicCancellationAppointmentDto.builder()
                        .serviceName("Corte + barba")
                        .staffMemberName("Mateo Ruiz")
                        .startsAt(startsAt)
                        .endsAt(endsAt)
                        .status(status)
                        .build())
                .business(PublicCancellationBusinessDto.builder()
                        .name("Barber Studio")
                        .timezone("America/Argentina/Buenos_Aires")
                        .build())
                .isCanCancel(canCancel)
                .build();
    }
}
