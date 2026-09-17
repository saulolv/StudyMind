package com.contentservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class WebMvcConfigurationTest {

    /** Without this registration every {@code @CurrentUser} parameter silently resolves to null. */
    @Test
    void registersTheCurrentUserResolver() {
        CurrentUserArgumentResolver currentUser = new CurrentUserArgumentResolver();
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        new WebMvcConfiguration(currentUser).addArgumentResolvers(resolvers);

        assertThat(resolvers).containsExactly(currentUser);
    }
}
