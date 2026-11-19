package com.turnero.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.session")
public class SessionProperties {

    private String cookieName;
    private String customerCookieName;
    private long ttlDays;
    private boolean secure;
    private String sameSite;

    @jakarta.annotation.PostConstruct
    void validateCookieNames() {
        if (getCustomerCookieName().equals(cookieName)) {
            throw new IllegalStateException("Admin and customer cookies must have different names");
        }
    }

    public String getCustomerCookieName() {
        return customerCookieName == null
                ? (secure ? "__Host-turnero_customer_session" : "turnero_customer_session") : customerCookieName;
    }

    public void setCustomerCookieName(String customerCookieName) {
        this.customerCookieName = customerCookieName;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public long getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(long ttlDays) {
        this.ttlDays = ttlDays;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }
}
