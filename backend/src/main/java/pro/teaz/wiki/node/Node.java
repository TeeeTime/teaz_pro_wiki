package pro.teaz.wiki.node;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/** A category or a page. Both have content; only categories have children (NodeService's depth rules). */
@Entity
public class Node {

	public enum Kind {
		CATEGORY, PAGE
	}

	/** parent_key of top-level nodes, see V1__init.sql. */
	static final UUID ROOT_KEY = new UUID(0, 0);

	static final String EMPTY_CONTENT = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";

	@Id
	private UUID id;

	@Column(nullable = false)
	private String wikiId;

	private UUID parentId;

	@Column(nullable = false)
	private UUID parentKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String slug;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private String content;

	@Column(nullable = false)
	private String plainText;

	/** Counts saves by people (not moves), so a stale editor gets 409 instead of overwriting. */
	@Column(nullable = false)
	private int version;

	@Column(nullable = false)
	private String createdBy;

	@Column(nullable = false)
	private String updatedBy;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Node() {
	}

	public Node(String wikiId, UUID parentId, Kind kind, String title, String slug, int position, String userId) {
		this.id = UUID.randomUUID();
		this.wikiId = wikiId;
		setParent(parentId, position);
		this.kind = kind;
		this.title = title;
		this.slug = slug;
		this.content = EMPTY_CONTENT;
		this.plainText = "";
		this.createdBy = userId;
		this.updatedBy = userId;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	public void setParent(UUID parentId, int position) {
		this.parentId = parentId;
		this.parentKey = parentId == null ? ROOT_KEY : parentId;
		this.position = position;
	}

	public void rename(String title, String slug) {
		this.title = title;
		this.slug = slug;
	}

	public void setContent(String content, String plainText) {
		this.content = content;
		this.plainText = plainText;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public void touch(String userId) {
		this.version++;
		this.updatedBy = userId;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getWikiId() {
		return wikiId;
	}

	public UUID getParentId() {
		return parentId;
	}

	public Kind getKind() {
		return kind;
	}

	public String getTitle() {
		return title;
	}

	public String getSlug() {
		return slug;
	}

	public int getPosition() {
		return position;
	}

	public String getContent() {
		return content;
	}

	public String getPlainText() {
		return plainText;
	}

	public int getVersion() {
		return version;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
