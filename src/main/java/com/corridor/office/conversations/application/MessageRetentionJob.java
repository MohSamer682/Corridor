package com.corridor.office.conversations.application;

import com.corridor.office.conversations.infrastructure.MessageRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Chat is kept 90 days. */
@Component
public class MessageRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionJob.class);
    private static final Duration KEEP = Duration.ofDays(90);

    private final MessageRepository messages;

    MessageRetentionJob(MessageRepository messages) {
        this.messages = messages;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteOldMessages() {
        var deleted = messages.deleteOlderThan(Instant.now().minus(KEEP));
        log.info("Deleted {} chat messages older than {} days", deleted, KEEP.toDays());
    }
}
