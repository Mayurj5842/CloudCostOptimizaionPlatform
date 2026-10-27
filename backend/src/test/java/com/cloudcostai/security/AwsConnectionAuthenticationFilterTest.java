package com.cloudcostai.security;

import com.cloudcostai.aws.repository.AwsConnectionSessionRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.impl.AwsConnectionServiceImpl.AwsIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConnectionAuthenticationFilterTest {
    private final TestableFilter filter = new TestableFilter();

    @Test
    void notificationCollectionAndNestedRoutesDoNotRequireAwsToken() {
        assertThat(filter.shouldSkip(request("/api/notifications"))).isTrue();
        assertThat(filter.shouldSkip(request("/api/notifications/preferences"))).isTrue();
        assertThat(filter.shouldSkip(request("/api/notifications/123/read"))).isTrue();
    }

    @Test
    void unrelatedAwsApiStillRequiresAwsToken() {
        assertThat(filter.shouldSkip(request("/api/aws/resources"))).isFalse();
        assertThat(filter.shouldSkip(request("/api/aws/live-cost"))).isFalse();
        assertThat(filter.shouldSkip(request("/api/aws/optimization/simulator"))).isFalse();
    }

    @Test
    void liveCostEndpointAuthenticatesTheSuppliedConnectionToken() throws Exception {
        String accountId = "123456789012";
        String token = filter.createSession(accountId);
        MockHttpServletRequest request = request("/api/aws/live-cost");
        request.addHeader("X-AWS-Connection-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reachedController = {false};

        try {
            filter.filter(request, response, (servletRequest, servletResponse) -> {
                reachedController[0] = true;
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                assertThat(authentication).isNotNull();
                assertThat(authentication.getPrincipal()).isEqualTo(accountId);
                assertThat(authentication.getAuthorities()).extracting("authority")
                        .containsExactly("ROLE_AWS_USER");
            });
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(reachedController[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void liveCostEndpointRejectsMissingConnectionTokenAsUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.filter(request("/api/aws/live-cost"), response, (servletRequest, servletResponse) -> {
            throw new AssertionError("The request must not reach the controller without an AWS token.");
        });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void liveCostEndpointRejectsStaleConnectionTokenAsUnauthorized() throws Exception {
        MockHttpServletRequest request = request("/api/aws/live-cost");
        request.addHeader("X-AWS-Connection-Token", "expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.filter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("The request must not reach the controller with an expired AWS token.");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
            .contains("AWS connection expired. Please reconnect your AWS account.");
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static class TestableFilter extends AwsConnectionAuthenticationFilter {
        private final AwsConnectionSessionService sessionService;

        private TestableFilter() {
            this(new AwsConnectionSessionService(Mockito.mock(AwsConnectionSessionRepository.class)));
        }

        private TestableFilter(AwsConnectionSessionService sessionService) {
            super(sessionService);
            this.sessionService = sessionService;
        }

        private boolean shouldSkip(HttpServletRequest request) {
            return super.shouldNotFilter(request);
        }

        private String createSession(String accountId) {
            return sessionService.createSession("access", "secret", "us-east-1",
                    new AwsIdentity(accountId, "arn:aws:iam::" + accountId + ":user/test", "AIDAEXAMPLE"));
        }

        private void filter(HttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
                throws Exception {
            super.doFilterInternal(request, response, chain);
        }
    }
}