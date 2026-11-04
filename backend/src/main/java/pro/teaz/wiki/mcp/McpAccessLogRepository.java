package pro.teaz.wiki.mcp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface McpAccessLogRepository extends JpaRepository<McpAccessLog, UUID> {

	List<McpAccessLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

	Slice<McpAccessLog> findByWikiIdOrderByCreatedAtDesc(String wikiId, Pageable pageable);

	@Modifying
	@Query("delete from McpAccessLog entry where entry.createdAt < :before")
	int deleteOlderThan(Instant before);
}
