package com.corridor.office.meetings.application;

import com.corridor.office.common.idempotency.IdempotencyGuard;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.meetings.application.MeetingService.MeetingContext;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class MeetingConfig {

    @Bean
    MeetingContext meetingContext(
            PlatformTransactionManager transactions,
            IdempotencyGuard guard,
            UserDirectory directory,
            ApplicationEventPublisher events,
            Clock clock) {
        var readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        return new MeetingContext(new TransactionTemplate(transactions), readOnly, guard, directory, events, clock);
    }
}
