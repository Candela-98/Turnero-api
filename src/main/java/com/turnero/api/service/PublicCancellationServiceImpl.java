package com.turnero.api.service;

import com.turnero.api.dto.*;
import com.turnero.api.exception.*;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.repository.*;
import com.turnero.api.security.PublicTokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class PublicCancellationServiceImpl implements PublicCancellationService{

    private final AppointmentPublicTokenRepository appointmentPublicTokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final BookingSettingsRepository bookingSettingsRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final PublicTokenHasher publicTokenHasher;

    @Transactional(readOnly = true)
    @Override
    public PublicCancellationResponseDto getPublicCancellation(String token) {

        String tokenHash = publicTokenHasher.hash(token);

        AppointmentPublicToken publicToken = appointmentPublicTokenRepository.findByTokenHashAndType(tokenHash, AppointmentPublicTokenType.CANCEL)
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        if(publicToken.getUsedAt() != null){
            throw new PublicTokenUsedException("This cancellation token has already been used");
        }

        Appointment appointment = appointmentRepository.findById(publicToken.getAppointmentId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        Business business = businessRepository.findById(appointment.getBusinessId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        ZoneId businessZone = ZoneId.of(business.getTimezone());
        LocalDateTime now = LocalDateTime.now(businessZone);

        if(!publicToken.getExpiresAt().isAfter(now)) {
            throw new PublicTokenExpiredException("Public cancellation token has expired");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("Appointment cannot be cancelled from status " + appointment.getStatus());
        }

        BookingSettings bookingSettings = bookingSettingsRepository.findByBusinessId(business.getId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        LocalDateTime cancellationDeadline = appointment.getStartsAt().minusHours(bookingSettings.getCancellationNoticeHours());

        ServiceOffering serviceOffering = servOfferingRepository.findByIdAndBusinessId(appointment.getServiceOfferingId(), appointment.getBusinessId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        StaffMember staffMember = staffMemberRepository.findByIdAndBusinessId(appointment.getStaffMemberId(), appointment.getBusinessId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        PublicCancellationAppointmentDto appointmentDto = PublicCancellationAppointmentDto.builder()
                .serviceName(serviceOffering.getName())
                .staffMemberName(staffMember.getName())
                .startsAt(appointment.getStartsAt())
                .endsAt(appointment.getEndsAt())
                .status(appointment.getStatus())
                .build();

        PublicCancellationBusinessDto businessDto = PublicCancellationBusinessDto.builder()
                .name(business.getName())
                .timezone(business.getTimezone())
                .build();

        return PublicCancellationResponseDto.builder()
                .business(businessDto)
                .appointment(appointmentDto)
                .isCanCancel(now.isBefore(cancellationDeadline))
                .build();

    }

    @Transactional
    @Override
    public PublicCancellationResultResponseDto cancelPublicAppointment(String token, PublicCancellationRequestDto request) {

        String tokenHash = publicTokenHasher.hash(token);

        AppointmentPublicToken publicToken = appointmentPublicTokenRepository.findByTokenHashAndTypeForUpdate(tokenHash, AppointmentPublicTokenType.CANCEL)
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        if (publicToken.getUsedAt() != null) {
            throw new PublicTokenUsedException("This cancellation token has already been used");
        }

        Appointment appointment = appointmentRepository.findById(publicToken.getAppointmentId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        Business business = businessRepository.findById(appointment.getBusinessId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token")
                );

        ZoneId businessZone = ZoneId.of(business.getTimezone());
        LocalDateTime now = LocalDateTime.now(businessZone);

        if (!publicToken.getExpiresAt().isAfter(now)) {
            throw new PublicTokenExpiredException("Public cancellation token has expired");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.CONFIRMED) {

            throw new InvalidStateTransitionException("Appointment cannot be cancelled from status " + appointment.getStatus());
        }

        BookingSettings bookingSettings = bookingSettingsRepository.findByBusinessId(business.getId())
                .orElseThrow(() -> new InvalidPublicTokenException("Invalid public cancellation token"));

        LocalDateTime cancellationDeadline = appointment.getStartsAt().minusHours(bookingSettings.getCancellationNoticeHours());

        if (!now.isBefore(cancellationDeadline)) {
            throw new InvalidStateTransitionException("Appointment can no longer be cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.getCancellationReason());
        appointment.setUpdatedAt(now);

        publicToken.setUsedAt(now);

        appointmentRepository.save(appointment);
        appointmentPublicTokenRepository.save(publicToken);

        return PublicCancellationResultResponseDto.builder()
                .status(AppointmentStatus.CANCELLED)
                .message("Tu turno fue cancelado")
                .build();
    }


}
