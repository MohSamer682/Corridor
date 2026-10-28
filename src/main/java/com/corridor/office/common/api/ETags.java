package com.corridor.office.common.api;

import com.corridor.office.common.domain.PreconditionRequiredException;

/** Entity versions travel as strong ETags ("3") and come back in If-Match. */
public final class ETags {

    private ETags() {}

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    public static long version(String ifMatch, String missingMessageKey) {
        if (ifMatch == null) {
            throw new PreconditionRequiredException(missingMessageKey);
        }
        try {
            return Long.parseLong(ifMatch.replace("W/", "").replace("\"", "").strip());
        } catch (NumberFormatException malformed) {
            throw new PreconditionRequiredException(missingMessageKey);
        }
    }
}
