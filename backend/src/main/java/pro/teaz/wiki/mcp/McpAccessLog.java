package pro.teaz.wiki.mcp;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** One MCP tool call, for the user to audit what an AI assistant actually read. */
@Entity
public class McpAccessLog {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String userId;

	/** Null for tool calls not scoped to one wiki (e.g. listWikis). */
	private String wikiId;

	@Column(nullable = false)
	private String tool;

	private String query;

	@Column(nullable = false)
	private Instant createdAt;

	protected McpAccessLog() {
	}

	public McpAccessLog(String userId, String wikiId, String tool, String query) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.wikiId = wikiId;
		this.tool = tool;
		this.query = query;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getWikiId() {
		return wikiId;
	}

	public String getTool() {
		return tool;
	}

	public String getQuery() {
		return query;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
