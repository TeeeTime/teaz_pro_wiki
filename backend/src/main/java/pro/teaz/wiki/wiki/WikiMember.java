package pro.teaz.wiki.wiki;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

/** Someone the owner added to a wiki. The owner is never a member of their own wiki. */
@Entity
@IdClass(WikiMember.Key.class)
public class WikiMember {

	public enum Role {
		/** Creates, edits, moves and deletes categories and pages. */
		EDITOR,
		/** Reads a private wiki (in a public one everybody reads anyway). */
		VIEWER
	}

	public record Key(String wikiId, String userId) implements Serializable {
		public Key() {
			this(null, null);
		}
	}

	@Id
	private String wikiId;

	@Id
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private Instant addedAt;

	protected WikiMember() {
	}

	public WikiMember(String wikiId, String userId, Role role) {
		this.wikiId = wikiId;
		this.userId = userId;
		this.role = role;
		this.addedAt = Instant.now();
	}

	public String getWikiId() {
		return wikiId;
	}

	public String getUserId() {
		return userId;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Instant getAddedAt() {
		return addedAt;
	}
}
