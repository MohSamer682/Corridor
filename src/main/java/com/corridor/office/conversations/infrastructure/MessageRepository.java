package com.corridor.office.conversations.infrastructure;

import com.corridor.office.conversations.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query(
            value =
                    """
                    SELECT * FROM messages
                    WHERE organization_id = :organizationId AND floor_id = :floorId AND :reader = ANY (recipients)
                    ORDER BY created_at DESC, id DESC
                    LIMIT :limit""",
            nativeQuery = true)
    List<Message> newest(UUID organizationId, UUID floorId, UUID reader, int limit);

    @Query(
            value =
                    """
                    SELECT m.* FROM messages m, messages b
                    WHERE b.id = :before AND m.organization_id = :organizationId AND m.floor_id = :floorId
                      AND :reader = ANY (m.recipients) AND (m.created_at, m.id) < (b.created_at, b.id)
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT :limit""",
            nativeQuery = true)
    List<Message> before(UUID organizationId, UUID floorId, UUID reader, UUID before, int limit);

    @Modifying
    @Query("delete from Message m where m.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
