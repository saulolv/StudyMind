package com.contentservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * This resolver is the trust boundary: it is the only thing standing between "a request arrived"
 * and "a user owns this content". Anything it cannot turn into a UUID must be rejected, not
 * defaulted.
 */
class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @Test
    void resolvesOnlyUuidParametersAnnotatedWithCurrentUser() {
        assertThat(resolver.supportsParameter(parameterOf("annotatedUuid", UUID.class))).isTrue();
        assertThat(resolver.supportsParameter(parameterOf("annotatedString", String.class))).isFalse();
        assertThat(resolver.supportsParameter(parameterOf("plainUuid", UUID.class))).isFalse();
    }

    @Test
    void readsTheUserIdTheGatewayForwarded() {
        UUID user = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(resolve(user.toString())).isEqualTo(user);
    }

    @Test
    void refusesARequestWithNoUserIdHeader() {
        assertThatThrownBy(() -> resolve(null))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    void refusesABlankUserIdHeader() {
        assertThatThrownBy(() -> resolve("  "))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    void refusesAUserIdThatIsNotAUuid() {
        assertThatThrownBy(() -> resolve("not-a-uuid"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("Malformed");
    }

    private Object resolve(String header) {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader(CurrentUserArgumentResolver.USER_ID_HEADER)).thenReturn(header);
        return resolver.resolveArgument(
                parameterOf("annotatedUuid", UUID.class), null, request, null);
    }

    private static MethodParameter parameterOf(String methodName, Class<?> parameterType) {
        try {
            return new MethodParameter(Signatures.class.getDeclaredMethod(methodName, parameterType), 0);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(ex);
        }
    }

    /** Parameter shapes the resolver has to tell apart, declared here so it can read the annotations. */
    @SuppressWarnings("unused")
    private static final class Signatures {

        void annotatedUuid(@CurrentUser UUID userId) {
        }

        void annotatedString(@CurrentUser String userId) {
        }

        void plainUuid(UUID userId) {
        }
    }
}
