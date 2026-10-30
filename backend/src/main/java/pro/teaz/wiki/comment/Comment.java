package pro.teaz.wiki.comment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A comment under a category or page; a reply when parentId is set (always a top-level comment). */
@Entity
public class Comment {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID nodeId;

	private UUID parentId;

	@Column(nullable = false)
	private String authorId;

	@Column(nullable = false)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant editedAt;

	@Column(nullable = false)
	private boolean deleted;

	protected Comment() {
	}

	public Comment(UUID nodeId, UUID parentId, String authorId, String body) {
		this.id = UUID.randomUUID();
		this.nodeId = nodeId;
		this.parentId = parentId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = Instant.now();
	}

	public void edit(String body) {
		this.body = body;
		this.editedAt = Instant.now();
	}

	/** Keeps the row (and its replies), drops the text. */
	public void markDeleted() {
		this.body = "";
		this.deleted = true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getNodeId() {
		return nodeId;
	}

	public UUID getParentId() {
		return parentId;
	}

	public String getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEditedAt() {
		return editedAt;
	}

	public boolean isDeleted() {
		return deleted;
	}
}
