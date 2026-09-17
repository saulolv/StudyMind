package com.contentservice.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated user id.
 *
 * <p>Trust boundary: the api-gateway-auth service validates the JWT and forwards the subject as
 * {@code X-User-Id}. This service never sees a token and must therefore never be reachable from
 * outside the internal network.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
