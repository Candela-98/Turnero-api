package com.turnero.api.context;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
/**Contexto temporal de negocio para desarrollo y testing.
 Actualmente devuelve el business seed (id = 1).
 Será reemplazado por un contexto basado en el usuario autenticado
 cuando se implemente Google Auth.
 **/
public class DevCurrentBusinessContext implements CurrentBusinessContext{
    private static final Long DEV_BUSINESS_ID = 1L;

    @Override
    public Long getCurrentBusinessId() {
        return DEV_BUSINESS_ID;
    }
}
