package com.turnero.api.dto;

import jakarta.validation.constraints.NotNull;

public class StaffMemberRequestDto {
    @NotNull(message = "The StaffMember ID is mandatory.")
    private Long staffMemberId;

    @NotNull(message = "The name of the staffmember is mandatory.")
    private String nameStaffMember;

    @NotNull(message = "The staffmember's specialty is mandatory.")
    private String speciality;

    @NotNull(message = "The staffmember's license is mandatory.")
    private String license;

    //Getters y Setters

    public Long getStaffMemberId() {
        return staffMemberId;
    }
    public void setStaffMemberId(Long staffMemberId) {
        this.staffMemberId = staffMemberId;
    }

    public String getNameStaffMember() {
        return nameStaffMember;
    }
    public void setNameStaffMember(String nameStaffMember) {
        this.nameStaffMember = nameStaffMember;
    }

    public String getSpeciality() {
        return speciality;
    }
    public void setSpeciality(String speciality) {
        this.speciality = speciality;
    }

    public String getLicense() {
        return license;
    }
    public void setLicense(String license) {
        this.license = license;
    }
}
