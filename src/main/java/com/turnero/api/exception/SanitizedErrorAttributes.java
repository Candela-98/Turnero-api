package com.turnero.api.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/** Also protect servlet /error responses that bypass the MVC exception advice. */
@Component
public class SanitizedErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        if (attributes.get("path") instanceof String path) {
            attributes.put("path", ErrorPathSanitizer.sanitize(path));
        }
        return attributes;
    }
}
