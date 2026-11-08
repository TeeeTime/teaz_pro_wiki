package pro.teaz.wiki.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
	/** mcp_access_log.query is varchar(500), wiki_id char(8). */
	private static final int MAX_QUERY = 500;
	private static final Pattern WIKI_ID = Pattern.compile("[a-z0-9]{8}");

	private final McpAccessLogRepository log;
	private final Users users;

	public McpAccessLogService(McpAccessLogRepository log, Users users) {
		this.log = log;
		this.users = users;
	}

	/**
	 * Called with whatever the AI assistant sent, before any check. It must fit
	 * the columns: otherwise the insert fails and takes the tool call with it.
	 */
	@Transactional
	public void record(String userId, String wikiId, String tool, String query) {
		log.save(new McpAccessLog(userId, isWikiId(wikiId) ? wikiId : null, tool, shorten(query)));
	}

	/** Same shape as Wiki ids. Anything else names no wiki, so it belongs in no wiki's log either. */
	private static boolean isWikiId(String wikiId) {
		return wikiId != null && WIKI_ID.matcher(wikiId).matches();
	}

	/** Cut to the column's 500, counted in UTF-16 units (H2), so code points (Postgres) fit too. */
	static String shorten(String query) {
		if (query == null || query.length() <= MAX_QUERY) return query;
		int end = MAX_QUERY - 1;
		if (Character.isHighSurrogate(query.charAt(end - 1))) end--;
		return query.substring(0, end) + "…";
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
