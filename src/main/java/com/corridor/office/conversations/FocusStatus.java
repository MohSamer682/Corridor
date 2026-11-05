package com.corridor.office.conversations;

import java.util.UUID;

/** Whether someone is in focus mode, for other modules. */
public interface FocusStatus {

    boolean inFocus(UUID userId);
}
