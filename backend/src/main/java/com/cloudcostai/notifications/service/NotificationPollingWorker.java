package com.cloudcostai.notifications.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class NotificationPollingWorker {
    private static final Logger logger = LoggerFactory.getLogger(NotificationPollingWorker.class);
    private final NotificationService notificationService;
    private ScheduledExecutorService executor;

    public NotificationPollingWorker(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudcost-notification-poller");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                notificationService.evaluateEnabledPreferences();
            } catch (RuntimeException exception) {
                logger.warn("Notification polling failed: {}", exception.getMessage());
            }
        }, 1, 15, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stop() {
        if (executor != null) executor.shutdownNow();
    }
}