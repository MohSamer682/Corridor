package com.corridor.office.presence;

import java.util.Map;
import java.util.UUID;

/** Who is in the office right now, for other modules. */
public interface OnlinePeople {

    /** Everyone of a company standing on a floor right now, and which floor. */
    Map<UUID, UUID> floorsOf(UUID organizationId);
}
