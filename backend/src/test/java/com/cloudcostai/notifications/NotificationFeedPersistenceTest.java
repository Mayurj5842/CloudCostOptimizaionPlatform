package com.cloudcostai.notifications;

import com.cloudcostai.auth.model.AppUser;
import com.cloudcostai.auth.model.AppSession;
import com.cloudcostai.auth.repository.AppSessionRepository;
import com.cloudcostai.auth.repository.AppUserRepository;
import com.cloudcostai.notifications.model.NotificationLog;
import com.cloudcostai.notifications.repository.NotificationLogRepository;
import com.cloudcostai.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class NotificationFeedPersistenceTest {
    @Autowired private AppUserRepository userRepository;
        @Autowired private AppSessionRepository sessionRepository;
    @Autowired private NotificationLogRepository logRepository;
    @Autowired private NotificationService notificationService;
        @Autowired private WebApplicationContext webApplicationContext;
        private MockMvc mockMvc;

        @BeforeEach
        void setUpMockMvc() {
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        }

    @Test
    void feedAndReadUpdatesAreScopedToAuthenticatedUserAndSelectedAccount() {
        String suffix = UUID.randomUUID().toString();
        UUID userId = userRepository.save(new AppUser("feed-user-" + suffix, "feed-user-" + suffix + "@example.test", "hash"))
                .profile().id();
        UUID otherUserId = userRepository.save(new AppUser("other-user-" + suffix, "other-user-" + suffix + "@example.test", "hash"))
                .profile().id();
        String accountId = "100000000001";
        String otherAccountId = "100000000002";

        NotificationLog budgetAlert = saveLog(userId, accountId, "BUDGET_THRESHOLD", suffix + "-budget", "Budget threshold reached", "Spend crossed the configured threshold.");
        saveLog(userId, accountId, "MONTHLY_SUMMARY", suffix + "-summary", "Monthly cost summary", "Summary for the previous billing period.");
        saveLog(otherUserId, accountId, "COST_ANOMALY", suffix + "-other-user", "Cost anomaly", "Other user's anomaly.");
        saveLog(userId, otherAccountId, "BUDGET_EXCEEDED", suffix + "-other-account", "Budget exceeded", "Other account's alert.");
        saveLog(userId, accountId, "TEST", suffix + "-test", "Test email", "SMTP test message.");

        var initialFeed = notificationService.recent(userId, accountId);
        assertThat(initialFeed.notifications()).hasSize(2);
        assertThat(initialFeed.unreadCount()).isEqualTo(2);
        assertThat(initialFeed.notifications()).allMatch(notification -> !notification.read());

        notificationService.markRead(userId, accountId, budgetAlert.getId());
        var afterSingleRead = notificationService.recent(userId, accountId);
        assertThat(afterSingleRead.unreadCount()).isEqualTo(1);
        assertThat(afterSingleRead.notifications()).filteredOn(notification -> notification.id().equals(budgetAlert.getId()))
                .singleElement().extracting(notification -> notification.read()).isEqualTo(true);

        assertThat(notificationService.markAllRead(userId, accountId)).isEqualTo(1);
        assertThat(notificationService.recent(userId, accountId).unreadCount()).isZero();
        assertThat(notificationService.recent(otherUserId, accountId).unreadCount()).isEqualTo(1);
        assertThat(notificationService.recent(userId, otherAccountId).unreadCount()).isEqualTo(1);
    }

    @Test
    void authenticatedFeedAndReadEndpointsUseThePersistedAppSession() throws Exception {
        String suffix = UUID.randomUUID().toString();
        AppUser user = userRepository.save(new AppUser("api-user-" + suffix, "api-user-" + suffix + "@example.test", "hash"));
        String token = UUID.randomUUID().toString().replace("-", "");
        sessionRepository.save(new AppSession(token, user, Instant.now().plusSeconds(3600)));
        String accountId = "100000000003";
        NotificationLog notification = saveLog(user.profile().id(), accountId, "COST_ANOMALY",
                suffix + "-api-anomaly", "Unusual AWS cost increase detected", "Persisted anomaly details.");

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token)
                        .param("awsAccountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.notifications[0].title").value("Unusual AWS cost increase detected"));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + token).param("awsAccountId", accountId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token)
                        .param("awsAccountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.notifications[0].read").value(true));

        mockMvc.perform(get("/api/notifications").param("awsAccountId", accountId))
                .andExpect(status().isUnauthorized());
    }

    private NotificationLog saveLog(UUID userId, String accountId, String type, String eventKey,
                                    String title, String message) {
        return logRepository.save(new NotificationLog(userId, accountId, type, eventKey,
                "notifications@example.test", title, message));
    }
}