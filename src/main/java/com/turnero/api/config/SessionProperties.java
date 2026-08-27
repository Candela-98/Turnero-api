package com.turnero.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.session")
public class SessionProperties {

    private String cookieName;
    private long ttlDays;
    private boolean secure;
    private String sameSite;

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
