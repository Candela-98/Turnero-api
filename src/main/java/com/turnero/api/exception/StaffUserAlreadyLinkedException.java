package com.turnero.api.exception;

public class StaffUserAlreadyLinkedException extends RuntimeException {
    public StaffUserAlreadyLinkedException() {
        super("This user is already linked to a staff member");
    }
}
