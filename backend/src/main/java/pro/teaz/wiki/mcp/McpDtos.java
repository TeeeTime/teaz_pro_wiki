package pro.teaz.wiki.mcp;

import java.time.Instant;
import java.util.List;

import pro.teaz.wiki.users.Users;

/** JSON shapes returned by the MCP tools (WikiMcpTools) and the access log API. */
public final class McpDtos {

	private McpDtos() {
	}

	public record WikiRef(String id, String name, String description, boolean isPublic) {
	}

	/** @param snippet text around the first match, or the start of the text for title-only matches */
	public record SearchHit(String wikiId, String wikiName, String title, List<String> path, String snippet) {
	}

	public record PageContent(String wikiId, String title, List<String> path, String text) {
	}

	public record AccessLogEntry(String tool, String wikiId, String query, Instant at) {
	}

	/** @param user who the AI assistant acted for; null for a guest */
	public record WikiAccessEntry(Users.UserView user, String tool, String query, Instant at) {
	}

	public record WikiAccessPage(List<WikiAccessEntry> entries, boolean hasMore) {
	}
}
