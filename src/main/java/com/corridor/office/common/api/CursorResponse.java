package com.corridor.office.common.api;

import java.util.List;

/** A slice of a newest-first list; {@code nextCursor} is null on the last slice. */
public record CursorResponse<T>(List<T> items, String nextCursor) {}
