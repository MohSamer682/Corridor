package com.corridor.office.announcements.application;

import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Event publishing and time, together so the announcement services keep short constructors. */
@Component
record AnnouncementEvents(ApplicationEventPublisher publisher, Clock clock) {}
