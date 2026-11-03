package com.corridor.office.presence.application;

import java.util.Collection;

/** Closes live sockets by id, for people who must leave the office now. */
public interface OpenSockets {

    void close(Collection<String> socketIds);
}
