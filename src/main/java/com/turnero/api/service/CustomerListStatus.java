package com.turnero.api.service;

import com.turnero.api.model.enums.CustomerStatus;

/** Archive scope; kept separate from the persisted two-state enum. */
public enum CustomerListStatus {
    ACTIVE, INACTIVE, ALL;

    public CustomerStatus customerStatus() {
        return this == ALL ? null : CustomerStatus.valueOf(name());
    }
}
