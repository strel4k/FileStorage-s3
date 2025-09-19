package com.crudapp.filestorage.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class JwtPreAuthenticationToken extends AbstractAuthenticationToken {
    private final String token;
    public JwtPreAuthenticationToken(String token) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.token = token;
    }
    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }
}
