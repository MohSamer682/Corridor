package com.corridor.office.common.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs an action once per (person, key). The key row is written first, in the caller's transaction: a second
 * request with the same key waits on it, then gets the stored answer. If the action fails, the transaction rolls the
 * key back with it, so a retry runs again.
 */
@Component
public class IdempotencyGuard {

    private static final Duration KEEP = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    IdempotencyGuard(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T once(UUID userId, UUID key, Class<T> type, Supplier<T> action) {
        var claimed = jdbc.sql(
                        """
                        INSERT INTO idempotency_keys (user_id, key, created_at) VALUES (:user, :key, :now)
                        ON CONFLICT DO NOTHING""")
                .param("user", userId)
                .param("key", key)
                .param("now", Timestamp.from(clock.instant()))
                .update();
        if (claimed == 0) {
            var stored = jdbc.sql("SELECT body FROM idempotency_keys WHERE user_id = :user AND key = :key")
                    .param("user", userId)
                    .param("key", key)
                    .query(String.class)
                    .single();
            return json.readValue(stored, type);
        }
        var result = action.get();
        jdbc.sql("UPDATE idempotency_keys SET body = :body WHERE user_id = :user AND key = :key")
                .param("body", json.writeValueAsString(result))
                .param("user", userId)
                .param("key", key)
                .update();
        return result;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void forgetOldKeys() {
        jdbc.sql("DELETE FROM idempotency_keys WHERE created_at < :cutoff")
                .param("cutoff", Timestamp.from(clock.instant().minus(KEEP)))
                .update();
    }
}
