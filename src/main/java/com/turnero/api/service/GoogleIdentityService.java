package com.turnero.api.service;

import com.turnero.api.dto.GoogleIdentityDto;

public interface GoogleIdentityService {

    GoogleIdentityDto verify(String idToken);
}
