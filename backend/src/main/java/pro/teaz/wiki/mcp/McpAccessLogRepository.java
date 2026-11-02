package pro.teaz.wiki.mcp;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpAccessLogRepository extends JpaRepository<McpAccessLog, UUID> {

	List<McpAccessLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
