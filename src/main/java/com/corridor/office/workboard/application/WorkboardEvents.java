package com.corridor.office.workboard.application;

import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Event publishing and time, together so the board service keeps a short constructor. */
@Component
record WorkboardEvents(ApplicationEventPublisher publisher, Clock clock) {}
