package com.turnero.api.exception;

public class StaffHasFutureAppointmentsException extends RuntimeException {
    public StaffHasFutureAppointmentsException() {
        super("Staff member cannot be deactivated because it has future active appointments");
    }
}
