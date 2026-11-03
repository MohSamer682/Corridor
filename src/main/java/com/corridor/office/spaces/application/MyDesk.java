package com.corridor.office.spaces.application;

import java.util.UUID;

/** Where "Go to my desk" takes someone; kind is DESK for a claimed desk or OFFICE for a private office. */
public record MyDesk(UUID floorId, int x, int y, String kind, String roomName) {}
