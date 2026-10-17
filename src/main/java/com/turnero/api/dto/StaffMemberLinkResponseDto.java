package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StaffMemberLinkResponseDto(@JsonProperty("staff_member") StaffMemberResponseDto staffMember) {}
