package pro.teaz.wiki.mcp;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What an AI assistant read through the MCP server, for the user to audit (McpAccessLog). */
@Service
public class McpAccessLogService {

	private static final int RECENT = 50;

	private final McpAccessLogRepository log;

	public McpAccessLogService(McpAccessLogRepository log) {
		this.log = log;
	}

	@Transactional
	public void record(String userId, String wikiId, String tool, String query) {
		log.save(new McpAccessLog(userId, wikiId, tool, query));
	}

	@Transactional(readOnly = true)
	public List<McpDtos.AccessLogEntry> recent(String userId) {
		return log.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT)).stream()
				.map(entry -> new McpDtos.AccessLogEntry(entry.getTool(), entry.getWikiId(), entry.getQuery(),
						entry.getCreatedAt()))
				.toList();
	}
}
