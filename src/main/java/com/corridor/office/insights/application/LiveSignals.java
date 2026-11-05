package com.corridor.office.insights.application;

import com.corridor.office.conversations.FocusStatus;
import com.corridor.office.presence.OnlinePeople;
import org.springframework.stereotype.Component;

/** What is only in memory: who is on a floor and who is in focus. Together to keep the service's constructor short. */
@Component
record LiveSignals(OnlinePeople online, FocusStatus focus) {}
