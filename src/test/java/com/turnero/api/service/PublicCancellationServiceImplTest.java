package com.turnero.api.service;

import com.turnero.api.dto.PublicCancellationRequestDto;
import com.turnero.api.dto.PublicCancellationResponseDto;
import com.turnero.api.dto.PublicCancellationResultResponseDto;
import com.turnero.api.exception.InvalidPublicTokenException;
import com.turnero.api.exception.InvalidStateTransitionException;
import com.turnero.api.exception.PublicTokenExpiredException;
import com.turnero.api.exception.PublicTokenUsedException;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.model.Business;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.BookingSettingsRepository;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.security.PublicTokenHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicCancellationServiceImplTest {

    private static final String PLAIN_TOKEN = "plain-cancel-token";
    private static final String TOKEN_HASH = "hashed-cancel-token";
    private static final Long BUSINESS_ID = 1L;
    private static final Long APPOINTMENT_ID = 900L;
    private static final Long SERVICE_OFFERING_ID = 10L;
    private static final Long STAFF_MEMBER_ID = 100L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock private AppointmentPublicTokenRepository appointmentPublicTokenRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private BookingSettingsRepository bookingSettingsRepository;
    @Mock private ServOfferingRepository servOfferingRepository;
    @Mock private StaffMemberRepository staffMemberRepository;
    @Mock private PublicTokenHasher publicTokenHasher;

    @InjectMocks private PublicCancellationServiceImpl publicCancellationService;

    @Test
    void getPublicCancellation_whenTokenIsValidAndConfirmedAppointmentIsBeforeDeadline_returnsPublicSummary() {
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);
        LocalDateTime endsAt = startsAt.plusMinutes(45);
        BookingSettings settings = bookingSettings(24);

        givenValidTokenFlow(appointment(AppointmentStatus.CONFIRMED, startsAt, endsAt), settings);

        PublicCancellationResponseDto response = publicCancellationService.getPublicCancellation(PLAIN_TOKEN);

        assertThat(response.getAppointment().getServiceName()).isEqualTo("Corte + barba");
        assertThat(response.getAppointment().getStaffMemberName()).isEqualTo("Mateo Ruiz");
        assertThat(response.getAppointment().getStartsAt()).isEqualTo(startsAt);
        assertThat(response.getAppointment().getEndsAt()).isEqualTo(endsAt);
        assertThat(response.getAppointment().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.getBusiness().getName()).isEqualTo("Barber Studio");
        assertThat(response.getBusiness().getTimezone()).isEqualTo(BUSINESS_ZONE.getId());
        assertThat(response.isCanCancel()).isTrue();
    }

    @Test
    void getPublicCancellation_whenAppointmentIsPending_returnsPublicSummary() {
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);
        LocalDateTime endsAt = startsAt.plusMinutes(45);

        givenValidTokenFlow(appointment(AppointmentStatus.PENDING, startsAt, endsAt), bookingSettings(24));

        PublicCancellationResponseDto response = publicCancellationService.getPublicCancellation(PLAIN_TOKEN);

        assertThat(response.getAppointment().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.isCanCancel()).isTrue();
    }

    @Test
    void getPublicCancellation_whenTokenDoesNotExist_throwsInvalidPublicTokenAndDoesNotReadAppointment() {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.getPublicCancellation(PLAIN_TOKEN))
                .isInstanceOf(InvalidPublicTokenException.class)
                .hasMessage("Invalid public cancellation token");

        verify(appointmentRepository, never()).findById(APPOINTMENT_ID);
    }

    @Test
    void getPublicCancellation_whenTokenExistsForAnotherType_behavesAsInvalidToken() {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.getPublicCancellation(PLAIN_TOKEN))
                .isInstanceOf(InvalidPublicTokenException.class);

        verify(appointmentPublicTokenRepository)
                .findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL);
        verify(appointmentRepository, never()).findById(APPOINTMENT_ID);
    }

    @Test
    void getPublicCancellation_whenTokenWasUsed_throwsPublicTokenUsedAndDoesNotReadAppointment() {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.of(publicToken(LocalDateTime.of(2030, 9, 15, 10, 0),
                        LocalDateTime.of(2026, 9, 14, 9, 0))));

        assertThatThrownBy(() -> publicCancellationService.getPublicCancellation(PLAIN_TOKEN))
                .isInstanceOf(PublicTokenUsedException.class)
                .hasMessage("This cancellation token has already been used");

        verify(appointmentRepository, never()).findById(APPOINTMENT_ID);
    }

    @Test
    void getPublicCancellation_whenTokenIsExpired_throwsPublicTokenExpired() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 15, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);
            localDateTime.when(LocalDateTime::now).thenReturn(fixedNow);

            givenAuthenticatedToken(publicToken(fixedNow.minusMinutes(1), null));
            given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
            given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(BUSINESS_ZONE.getId())));
            lenient().when(bookingSettingsRepository.findByBusinessId(BUSINESS_ID))
                    .thenReturn(Optional.of(bookingSettings(3)));

            assertThatThrownBy(() -> publicCancellationService.getPublicCancellation(PLAIN_TOKEN))
                    .isInstanceOf(PublicTokenExpiredException.class)
                    .hasMessage("Public cancellation token has expired");
        }
    }

    @Test
    void getPublicCancellation_whenAuthenticatedTokenReferencesMissingAppointment_throwsInvalidPublicToken() {
        givenAuthenticatedToken(publicToken(LocalDateTime.of(2030, 9, 15, 10, 0), null));
        given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.getPublicCancellation(PLAIN_TOKEN))
                .isInstanceOf(InvalidPublicTokenException.class)
                .hasMessage("Invalid public cancellation token");
    }

    @Test
    void getPublicCancellation_whenAppointmentIsCancelled_throwsInvalidStateTransition() {
        assertInvalidState(AppointmentStatus.CANCELLED);
    }

    @Test
    void getPublicCancellation_whenAppointmentIsCompleted_throwsInvalidStateTransition() {
        assertInvalidState(AppointmentStatus.COMPLETED);
    }

    @Test
    void getPublicCancellation_whenAppointmentIsNoShow_throwsInvalidStateTransition() {
        assertInvalidState(AppointmentStatus.NO_SHOW);
    }

    @Test
    void getPublicCancellation_whenAppointmentIsOutsideCancellationNotice_returnsCanCancelFalse() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 8, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 14, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);
            localDateTime.when(LocalDateTime::now).thenReturn(fixedNow);

            givenValidTokenFlow(appointment, bookingSettings(3));

            PublicCancellationResponseDto response = publicCancellationService.getPublicCancellation(PLAIN_TOKEN);

            assertThat(response.isCanCancel()).isFalse();
        }
    }

    @Test
    void getPublicCancellation_usesBusinessIdWhenLoadingServiceOfferingAndStaffMember() {
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));

        givenValidTokenFlow(appointment, bookingSettings(24));

        publicCancellationService.getPublicCancellation(PLAIN_TOKEN);

        verify(servOfferingRepository).findByIdAndBusinessId(SERVICE_OFFERING_ID, BUSINESS_ID);
        verify(staffMemberRepository).findByIdAndBusinessId(STAFF_MEMBER_ID, BUSINESS_ID);
    }

    @Test
    void getPublicCancellation_usesHasherOutputAndCancelTypeToFindToken() {
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);

        givenValidTokenFlow(appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45)),
                bookingSettings(24));

        publicCancellationService.getPublicCancellation(PLAIN_TOKEN);

        verify(publicTokenHasher).hash(PLAIN_TOKEN);
        verify(appointmentPublicTokenRepository)
                .findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL);
    }

    @Test
    void getPublicCancellation_usesBusinessTimezoneForExpirationAndCanCancel() {
        ZoneId businessZone = ZoneId.of("Pacific/Kiritimati");
        LocalDateTime businessNow = LocalDateTime.of(2026, 1, 2, 1, 0);
        LocalDateTime defaultServerNow = LocalDateTime.of(2026, 1, 2, 3, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 1, 2, 2, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 1, 2, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(businessNow);
            localDateTime.when(LocalDateTime::now).thenReturn(defaultServerNow);

            givenAuthenticatedToken(publicToken(expiresAt, null));
            given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
            given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(businessZone.getId())));
            given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(bookingSettings(2)));
            given(servOfferingRepository.findByIdAndBusinessId(SERVICE_OFFERING_ID, BUSINESS_ID))
                    .willReturn(Optional.of(serviceOffering()));
            given(staffMemberRepository.findByIdAndBusinessId(STAFF_MEMBER_ID, BUSINESS_ID))
                    .willReturn(Optional.of(staffMember()));

            PublicCancellationResponseDto response = publicCancellationService.getPublicCancellation(PLAIN_TOKEN);

            assertThat(response.isCanCancel()).isTrue();
        }
    }

    @Test
    void cancelPublicAppointment_whenPendingAppointmentIsBeforeDeadline_cancelsAppointmentConsumesTokenAndReturnsResult() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 8, 59);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 14, 12, 0);
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 9, 1, 9, 0);
        Appointment appointment = appointment(AppointmentStatus.PENDING, startsAt, startsAt.plusMinutes(45));
        appointment.setUpdatedAt(originalUpdatedAt);
        AppointmentPublicToken publicToken = publicToken(startsAt, null);
        PublicCancellationRequestDto request = cancellationRequest("No puedo asistir");

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenValidCancellationFlowForUpdate(publicToken, appointment, bookingSettings(3));

            PublicCancellationResultResponseDto response =
                    publicCancellationService.cancelPublicAppointment(PLAIN_TOKEN, request);

            assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            assertThat(response.getMessage()).isEqualTo("Tu turno fue cancelado");
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            assertThat(appointment.getCancellationReason()).isEqualTo("No puedo asistir");
            assertThat(appointment.getUpdatedAt()).isEqualTo(fixedNow);
            assertThat(publicToken.getUsedAt()).isEqualTo(fixedNow);

            ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
            ArgumentCaptor<AppointmentPublicToken> tokenCaptor = ArgumentCaptor.forClass(AppointmentPublicToken.class);
            verify(appointmentRepository).save(appointmentCaptor.capture());
            verify(appointmentPublicTokenRepository).save(tokenCaptor.capture());
            assertThat(appointmentCaptor.getValue()).isSameAs(appointment);
            assertThat(tokenCaptor.getValue()).isSameAs(publicToken);
        }
    }

    @Test
    void cancelPublicAppointment_whenConfirmedAppointmentIsBeforeDeadline_cancelsAppointment() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 8, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 15, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenValidCancellationFlowForUpdate(publicToken(startsAt, null), appointment, bookingSettings(24));

            PublicCancellationResultResponseDto response = publicCancellationService.cancelPublicAppointment(
                    PLAIN_TOKEN, cancellationRequest("Cambio de planes"));

            assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            assertThat(response.getMessage()).isEqualTo("Tu turno fue cancelado");
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            verify(appointmentRepository).save(appointment);
            verify(appointmentPublicTokenRepository).save(any(AppointmentPublicToken.class));
        }
    }

    @Test
    void cancelPublicAppointment_whenCancellationReasonIsNull_storesNullReason() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 8, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 15, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));
        PublicCancellationRequestDto request = cancellationRequest(null);

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenValidCancellationFlowForUpdate(publicToken(startsAt, null), appointment, bookingSettings(24));

            publicCancellationService.cancelPublicAppointment(PLAIN_TOKEN, request);

            assertThat(appointment.getCancellationReason()).isNull();
        }
    }

    @Test
    void cancelPublicAppointment_whenTokenDoesNotExist_throwsInvalidPublicTokenAndDoesNotReadAppointment() {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndTypeForUpdate(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                .isInstanceOf(InvalidPublicTokenException.class)
                .hasMessage("Invalid public cancellation token");

        verify(appointmentRepository, never()).findById(APPOINTMENT_ID);
        verifyNoCancellationPersisted();
    }

    @Test
    void cancelPublicAppointment_whenTokenExistsForAnotherType_behavesAsInvalidToken() {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndTypeForUpdate(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                .isInstanceOf(InvalidPublicTokenException.class);

        verify(appointmentPublicTokenRepository)
                .findByTokenHashAndTypeForUpdate(TOKEN_HASH, AppointmentPublicTokenType.CANCEL);
        verify(appointmentRepository, never()).findById(APPOINTMENT_ID);
        verifyNoCancellationPersisted();
    }

    @Test
    void cancelPublicAppointment_whenTokenWasUsed_throwsPublicTokenUsedAndDoesNotReadAppointment() {
        AppointmentPublicToken publicToken = publicToken(
                LocalDateTime.of(2030, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 14, 9, 0));

        givenAuthenticatedTokenForUpdate(publicToken);

        assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                .isInstanceOf(PublicTokenUsedException.class)
                .hasMessage("This cancellation token has already been used");

        assertThat(publicToken.getUsedAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 9, 0));
        verify(appointmentRepository, never()).findById(APPOINTMENT_ID);
        verifyNoCancellationPersisted();
    }

    @Test
    void cancelPublicAppointment_whenTokenIsExpired_throwsPublicTokenExpiredAndDoesNotChangeAppointmentOrToken() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 15, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));
        AppointmentPublicToken publicToken = publicToken(fixedNow.minusMinutes(1), null);

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenAuthenticatedTokenForUpdate(publicToken);
            given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
            given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(BUSINESS_ZONE.getId())));

            assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                    PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                    .isInstanceOf(PublicTokenExpiredException.class)
                    .hasMessage("Public cancellation token has expired");

            assertRejectedOperationDidNotChange(appointment, publicToken, AppointmentStatus.CONFIRMED);
        }
    }

    @Test
    void cancelPublicAppointment_whenAuthenticatedTokenReferencesMissingAppointment_throwsInvalidPublicToken() {
        AppointmentPublicToken publicToken = publicToken(LocalDateTime.of(2030, 9, 15, 10, 0), null);

        givenAuthenticatedTokenForUpdate(publicToken);
        given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                .isInstanceOf(InvalidPublicTokenException.class)
                .hasMessage("Invalid public cancellation token");

        assertThat(publicToken.getUsedAt()).isNull();
        verifyNoCancellationPersisted();
    }

    @Test
    void cancelPublicAppointment_whenAppointmentReferencesMissingBusiness_throwsInvalidPublicToken() {
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));
        AppointmentPublicToken publicToken = publicToken(startsAt, null);

        givenAuthenticatedTokenForUpdate(publicToken);
        given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
        given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                .isInstanceOf(InvalidPublicTokenException.class)
                .hasMessage("Invalid public cancellation token");

        assertRejectedOperationDidNotChange(appointment, publicToken, AppointmentStatus.CONFIRMED);
    }

    @Test
    void cancelPublicAppointment_whenAppointmentIsCancelled_throwsInvalidStateTransition() {
        assertCancelInvalidState(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelPublicAppointment_whenAppointmentIsCompleted_throwsInvalidStateTransition() {
        assertCancelInvalidState(AppointmentStatus.COMPLETED);
    }

    @Test
    void cancelPublicAppointment_whenAppointmentIsNoShow_throwsInvalidStateTransition() {
        assertCancelInvalidState(AppointmentStatus.NO_SHOW);
    }

    @Test
    void cancelPublicAppointment_whenCancellationDeadlineIsReached_rejectsCancellation() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 14, 12, 0);

        assertCancellationDeadlineRejected(fixedNow, startsAt, 3);
    }

    @Test
    void cancelPublicAppointment_whenCancellationDeadlineHasPassed_rejectsCancellation() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 1);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 14, 12, 0);

        assertCancellationDeadlineRejected(fixedNow, startsAt, 3);
    }

    @Test
    void cancelPublicAppointment_usesBusinessTimezoneForExpirationAndCancellationDeadline() {
        ZoneId businessZone = ZoneId.of("Pacific/Kiritimati");
        LocalDateTime businessNow = LocalDateTime.of(2026, 1, 2, 8, 0);
        LocalDateTime defaultServerNow = LocalDateTime.of(2026, 1, 1, 15, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 1, 2, 12, 0);
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));
        AppointmentPublicToken publicToken = publicToken(startsAt, null);

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(businessNow);
            localDateTime.when(LocalDateTime::now).thenReturn(defaultServerNow);

            givenAuthenticatedTokenForUpdate(publicToken);
            given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
            given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(businessZone.getId())));
            given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(bookingSettings(3)));

            publicCancellationService.cancelPublicAppointment(PLAIN_TOKEN, cancellationRequest("No puedo asistir"));

            assertThat(appointment.getUpdatedAt()).isEqualTo(businessNow);
            assertThat(publicToken.getUsedAt()).isEqualTo(businessNow);
        }
    }

    @Test
    void cancelPublicAppointment_hashesPlainTokenAndUsesLockedCancelTokenFinder() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 8, 0);
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 15, 10, 0);

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenValidCancellationFlowForUpdate(
                    publicToken(startsAt, null),
                    appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45)),
                    bookingSettings(24));

            publicCancellationService.cancelPublicAppointment(PLAIN_TOKEN, cancellationRequest("No puedo asistir"));

            verify(publicTokenHasher).hash(PLAIN_TOKEN);
            verify(appointmentPublicTokenRepository)
                    .findByTokenHashAndTypeForUpdate(TOKEN_HASH, AppointmentPublicTokenType.CANCEL);
            verify(appointmentPublicTokenRepository, never())
                    .findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL);
        }
    }

    private void assertInvalidState(AppointmentStatus status) {
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);

        givenAuthenticatedToken(publicToken(startsAt, null));
        given(appointmentRepository.findById(APPOINTMENT_ID))
                .willReturn(Optional.of(appointment(status, startsAt, startsAt.plusMinutes(45))));
        given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(BUSINESS_ZONE.getId())));

        assertThatThrownBy(() -> publicCancellationService.getPublicCancellation(PLAIN_TOKEN))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage("Appointment cannot be cancelled from status " + status);
    }

    private void assertCancelInvalidState(AppointmentStatus status) {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime startsAt = LocalDateTime.of(2030, 9, 15, 10, 0);
        Appointment appointment = appointment(status, startsAt, startsAt.plusMinutes(45));
        AppointmentPublicToken publicToken = publicToken(startsAt, null);

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenAuthenticatedTokenForUpdate(publicToken);
            given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
            given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(BUSINESS_ZONE.getId())));

            assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                    PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessage("Appointment cannot be cancelled from status " + status);

            assertRejectedOperationDidNotChange(appointment, publicToken, status);
            verify(bookingSettingsRepository, never()).findByBusinessId(BUSINESS_ID);
        }
    }

    private void assertCancellationDeadlineRejected(LocalDateTime fixedNow, LocalDateTime startsAt,
            int cancellationNoticeHours) {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, startsAt, startsAt.plusMinutes(45));
        AppointmentPublicToken publicToken = publicToken(startsAt, null);

        try (MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDateTime.when(() -> LocalDateTime.now(BUSINESS_ZONE)).thenReturn(fixedNow);

            givenValidCancellationFlowForUpdate(publicToken, appointment, bookingSettings(cancellationNoticeHours));

            assertThatThrownBy(() -> publicCancellationService.cancelPublicAppointment(
                    PLAIN_TOKEN, cancellationRequest("No puedo asistir")))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessage("Appointment can no longer be cancelled");

            assertRejectedOperationDidNotChange(appointment, publicToken, AppointmentStatus.CONFIRMED);
        }
    }

    private void assertRejectedOperationDidNotChange(Appointment appointment, AppointmentPublicToken publicToken,
            AppointmentStatus expectedStatus) {
        assertThat(appointment.getStatus()).isEqualTo(expectedStatus);
        assertThat(appointment.getCancellationReason()).isNull();
        assertThat(appointment.getUpdatedAt()).isNull();
        assertThat(publicToken.getUsedAt()).isNull();
        verifyNoCancellationPersisted();
    }

    private void verifyNoCancellationPersisted() {
        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(appointmentPublicTokenRepository, never()).save(any(AppointmentPublicToken.class));
    }

    private void givenValidTokenFlow(Appointment appointment, BookingSettings bookingSettings) {
        givenAuthenticatedToken(publicToken(appointment.getStartsAt(), null));
        given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
        given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(BUSINESS_ZONE.getId())));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(bookingSettings));
        given(servOfferingRepository.findByIdAndBusinessId(SERVICE_OFFERING_ID, BUSINESS_ID))
                .willReturn(Optional.of(serviceOffering()));
        given(staffMemberRepository.findByIdAndBusinessId(STAFF_MEMBER_ID, BUSINESS_ID))
                .willReturn(Optional.of(staffMember()));
    }

    private void givenAuthenticatedToken(AppointmentPublicToken publicToken) {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndType(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.of(publicToken));
    }

    private void givenValidCancellationFlowForUpdate(AppointmentPublicToken publicToken, Appointment appointment,
            BookingSettings bookingSettings) {
        givenAuthenticatedTokenForUpdate(publicToken);
        given(appointmentRepository.findById(APPOINTMENT_ID)).willReturn(Optional.of(appointment));
        given(businessRepository.findById(BUSINESS_ID)).willReturn(Optional.of(business(BUSINESS_ZONE.getId())));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(bookingSettings));
    }

    private void givenAuthenticatedTokenForUpdate(AppointmentPublicToken publicToken) {
        given(publicTokenHasher.hash(PLAIN_TOKEN)).willReturn(TOKEN_HASH);
        given(appointmentPublicTokenRepository.findByTokenHashAndTypeForUpdate(TOKEN_HASH, AppointmentPublicTokenType.CANCEL))
                .willReturn(Optional.of(publicToken));
    }

    private PublicCancellationRequestDto cancellationRequest(String cancellationReason) {
        PublicCancellationRequestDto request = new PublicCancellationRequestDto();
        request.setCancellationReason(cancellationReason);
        return request;
    }

    private AppointmentPublicToken publicToken(LocalDateTime expiresAt, LocalDateTime usedAt) {
        return AppointmentPublicToken.builder()
                .id(1L)
                .appointmentId(APPOINTMENT_ID)
                .tokenHash(TOKEN_HASH)
                .type(AppointmentPublicTokenType.CANCEL)
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .createdAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .build();
    }

    private Appointment appointment(AppointmentStatus status, LocalDateTime startsAt, LocalDateTime endsAt) {
        return Appointment.builder()
                .id(APPOINTMENT_ID)
                .businessId(BUSINESS_ID)
                .serviceOfferingId(SERVICE_OFFERING_ID)
                .staffMemberId(STAFF_MEMBER_ID)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(status)
                .build();
    }

    private Business business(String timezone) {
        return Business.builder()
                .id(BUSINESS_ID)
                .name("Barber Studio")
                .timezone(timezone)
                .status(BusinessStatus.ACTIVE)
                .build();
    }

    private BookingSettings bookingSettings(int cancellationNoticeHours) {
        return BookingSettings.builder()
                .id(1L)
                .businessId(BUSINESS_ID)
                .cancellationNoticeHours(cancellationNoticeHours)
                .build();
    }

    private ServiceOffering serviceOffering() {
        return ServiceOffering.builder()
                .id(SERVICE_OFFERING_ID)
                .businessId(BUSINESS_ID)
                .name("Corte + barba")
                .build();
    }

    private StaffMember staffMember() {
        return StaffMember.builder()
                .id(STAFF_MEMBER_ID)
                .businessId(BUSINESS_ID)
                .name("Mateo Ruiz")
                .build();
    }
}
