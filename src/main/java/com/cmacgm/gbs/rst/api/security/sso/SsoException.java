package com.cmacgm.gbs.rst.api.security.sso;

/**
 * SSO login failed. The callback redirects the browser instead of returning JSON.
 */
public class SsoException extends RuntimeException {

    private final String code;

    /**
     * @param code stable error token for the SPA query string
     * @param detail human-readable reason
     */
    public SsoException(String code, String detail) {
        super(detail);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
