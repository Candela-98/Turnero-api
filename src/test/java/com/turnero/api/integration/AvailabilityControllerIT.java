package com.turnero.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.dto.AvailabilitySlotResponseDto;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.*;
import com.turnero.api.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AvailabilityControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppointmentRepository appointmentRepository;

    @Autowired
    ServOfferingRepository servOfferingRepository;

    @Autowired
    StaffMemberRepository staffMemberRepository;

    @Autowired
    BusinessHoursRepository businessHoursRepository;

    @Autowired
    StaffWorkingHoursRepository staffWorkingHoursRepository;

    @Autowired
    BookingSettingsRepository bookingSettingsRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SessionService sessionService;

    @Autowired
    SessionProperties sessionProperties;

    @Autowired
    WebApplicationContext webApplicationContext;

    private static final String BASE_URL = "/api/v1/availability/slots";

    @BeforeEach
    void setUpAdminAuth() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").cookie(adminAuth().ownerSessionCookie(1L)))
                .build();
    }

    private AdminAuthTestHelper adminAuth() {
        return new AdminAuthTestHelper(userRepository, sessionService, sessionProperties);
    }

    //region Helper Methods
    private ServiceOffering getServiceOfferingEntity(){
        return ServiceOffering.builder()
                .businessId(1L)
                .name("Corte")
                .durationMinutes(30)
                .priceCents(15000)
                .build();
    }

    private StaffMember getStaffMemberEntity() {
        return StaffMember.builder()
                .businessId(1L)
                .name("Maria Gomez")
                .specialty("Corte")
                .build();
    }

    private BookingSettings getBookingSettingsEntity() {
        return BookingSettings.builder()
                .businessId(1L)
                .publicBookingEnabled(true)
                .requiresCustomerLogin(false)
                .bookingWindowDays(30)
                .minNoticeHours(0)
                .cancellationNoticeHours(0)
                .slotIntervalMinutes(30)
                .manualConfirmationEnabled(false)
                .whatsappRemindersEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private BusinessHours getBusinessHoursEntity(LocalDate date) {
        return BusinessHours.builder()
                .businessId(1L)
                .dayOfWeek(DayOfWeek.valueOf(date.getDayOfWeek().name()))
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();
    }

    private StaffWorkingHours getStaffWorkingHoursEntity(Long staffMemberId, LocalDate date) {
        return StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.valueOf(date.getDayOfWeek().name()))
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();
    }

    //endregion

    @Test
    void getAvailableSlots_whenBusinessAndStaffAreOpen_returns200AndSlots() throws Exception {
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();

        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getStartsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 0)));
        assertThat(response.get(0).getEndsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 30)));
        assertThat(response.get(0).isAvailable()).isTrue();
        assertThat(response.get(1).getStartsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 30)));
        assertThat(response.get(1).getEndsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(10, 0)));
        assertThat(response.get(1).isAvailable()).isTrue();
    }

    @Test
    void getAvailableSlots_whenBusinessIsClosed_returnsEmptyList() throws Exception{
        // Given
        BusinessHours closedBusinessHours = BusinessHours.builder()
                .businessId(1L)
                .dayOfWeek(DayOfWeek.MONDAY)
                .isClosed(true)
                .build();

        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), LocalDate.of(2026, 8, 3)));
        businessHoursRepository.save(closedBusinessHours);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", "2026-08-03")
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).isEmpty();
    }

    @Test
    void getAvailableSlots_whenStaffIsUnavailable_returnsEmptyList() throws Exception{
        // Given
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(LocalDate.of(2026, 8, 3)));

        StaffWorkingHours unavailableStaffHours = StaffWorkingHours.builder()
                .staffMemberId(staffMember.getId())
                .dayOfWeek(DayOfWeek.MONDAY)
                .isAvailable(false)
                .build();
        staffWorkingHoursRepository.save(unavailableStaffHours);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", "2026-08-03")
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).isEmpty();
    }

    @Test
    void getAvailableSlots_whenPendingAppointmentExists_excludesOccupiedSlot() throws Exception{
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        Appointment pendingAppointment = Appointment.builder()
                .businessId(1L)
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.PENDING)
                .build();
        appointmentRepository.save(pendingAppointment);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getStartsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 30)));
        assertThat(response.get(0).getEndsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(10, 0)));
    }

    @Test
    void getAvailableSlots_whenConfirmedAppointmentExists_excludesOccupiedSlot() throws Exception{
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        Appointment confirmedAppointment = Appointment.builder()
                .businessId(1L)
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.CONFIRMED)
                .build();
        appointmentRepository.save(confirmedAppointment);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getStartsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 30)));
        assertThat(response.get(0).getEndsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(10, 0)));
    }

    @Test
    void getAvailableSlots_whenNonBlockingAppointmentExists_doesNotExcludeSlot() throws Exception{
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        Appointment nonBlockingAppointment = Appointment.builder()
                .businessId(1L)
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.CANCELLED)
                .build();
        appointmentRepository.save(nonBlockingAppointment);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(2);
    }

    @Test
    void getAvailableSlots_whenCompletedAppointmentExists_doesNotExcludeSlot() throws Exception{
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        Appointment completedAppointment = Appointment.builder()
                .businessId(1L)
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.COMPLETED)
                .build();
        appointmentRepository.save(completedAppointment);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(2);
    }

    @Test
    void getAvailableSlots_whenNoShowAppointmentExists_doesNotExcludeSlot() throws Exception{
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());
        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        Appointment noShowAppointment = Appointment.builder()
                .businessId(1L)
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.NO_SHOW)
                .build();
        appointmentRepository.save(noShowAppointment);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();
        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(response).hasSize(2);
    }

    @Test
    void getAvailableSlots_whenRangeExceedsMaximumAllowed_returnsBadRequest() throws Exception{
        // Given
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = from.plusDays(31);
        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());
        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());

        // When + Then
        mockMvc.perform(get(BASE_URL)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAvailableSlots_whenExcludeAppointmentIdIsProvided_ignoresCurrentAppointment() throws Exception{
        // Given
        LocalDate date = LocalDate.of(2026, 8, 3);

        ServiceOffering serviceOffering = servOfferingRepository.save(getServiceOfferingEntity());

        StaffMember staffMember = staffMemberRepository.save(getStaffMemberEntity());

        bookingSettingsRepository.save(getBookingSettingsEntity());
        businessHoursRepository.save(getBusinessHoursEntity(date));
        staffWorkingHoursRepository.save(getStaffWorkingHoursEntity(staffMember.getId(), date));

        Appointment pendingAppointment = Appointment.builder()
                .businessId(1L)
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.PENDING)
                .build();

        pendingAppointment =
                appointmentRepository.save(pendingAppointment);

        // When
        MvcResult result = mockMvc.perform(get(BASE_URL)
                        .param("date", date.toString())
                        .param("service_offering_id", serviceOffering.getId().toString())
                        .param("staff_member_id", staffMember.getId().toString())
                        .param("exclude_appointment_id", pendingAppointment.getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then
        String json = result.getResponse().getContentAsString();

        List<AvailabilitySlotResponseDto> response = objectMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getStartsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 0)));
        assertThat(response.get(1).getStartsAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(9, 30)));
    }
}
