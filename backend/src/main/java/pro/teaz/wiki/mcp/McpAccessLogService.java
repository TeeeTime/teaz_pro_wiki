package pro.teaz.wiki.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pro.teaz.wiki.users.Users;

/** What an AI assistant read through the MCP server, for the user to audit (McpAccessLog). */
@Service
public class McpAccessLogService {

	private static final int RECENT = 50;
	private static final int PAGE_SIZE = 50;
	/** Entries hold what people searched for, so they don't stay around. */
	static final Duration RETENTION = Duration.ofDays(30);
	/** What WikiMcpTools records for callers without a login. */
	static final String GUEST = "guest";

	private final McpAccessLogRepository log;
	private final Users users;

	public McpAccessLogService(McpAccessLogRepository log, Users users) {
		this.log = log;
		this.users = users;
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

	/** Newest first, for the wiki's settings. Check the caller's rights first, this doesn't. */
	@Transactional(readOnly = true)
	public McpDtos.WikiAccessPage forWiki(String wikiId, int page) {
		Slice<McpAccessLog> entries = log.findByWikiIdOrderByCreatedAtDesc(wikiId, PageRequest.of(page, PAGE_SIZE));
		Map<String, Users.UserView> names = users.views(entries.stream().map(McpAccessLog::getUserId).toList());
		return new McpDtos.WikiAccessPage(entries.stream()
				.map(entry -> new McpDtos.WikiAccessEntry(GUEST.equals(entry.getUserId()) ? null : names.get(entry.getUserId()),
						entry.getTool(), entry.getQuery(), entry.getCreatedAt()))
				.toList(), entries.hasNext());
	}

	@Scheduled(cron = "0 30 3 * * *")
	@Transactional
	public void deleteExpired() {
		log.deleteOlderThan(Instant.now().minus(RETENTION));
	}
}
