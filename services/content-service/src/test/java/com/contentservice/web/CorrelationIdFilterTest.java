package com.contentservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The filter is the only reason {@code RabbitEventPublisher} can read a correlation id out of the
 * MDC instead of having it threaded through every call, so what matters is that it is set for the
 * duration of the request and removed afterwards.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsTheCorrelationIdTheCallerSent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seenDuringRequest = runThrough(request, response);

        assertThat(seenDuringRequest).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void inventsOneWhenTheHeaderIsAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seenDuringRequest = runThrough(new MockHttpServletRequest(), response);

        assertThat(UUID.fromString(seenDuringRequest)).isNotNull();
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(seenDuringRequest);
    }

    @Test
    void inventsOneWhenTheHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");

        String seenDuringRequest = runThrough(request, new MockHttpServletResponse());

        assertThat(UUID.fromString(seenDuringRequest)).isNotNull();
    }

    /** A pooled request thread must not leak one request's id into the next one's logs. */
    @Test
    void removesTheIdFromTheMdcOnceTheRequestIsDone() throws Exception {
        runThrough(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)).isNull();
    }

    private String runThrough(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        String[] seen = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
            }
        };
        filter.doFilter(request, response, chain);
        return seen[0];
    }
}
