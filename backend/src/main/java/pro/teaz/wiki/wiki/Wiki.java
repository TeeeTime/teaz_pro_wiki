package pro.teaz.wiki.wiki;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class Wiki {

	public enum Visibility {
		/** Only the owner and members. */
		PRIVATE,
		/** Everyone can read, also without login; members with EDITOR can edit. */
		PUBLIC
	}

	private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	/** 8 random characters [a-z0-9], the wiki's first URL segment. */
	@Id
	private String id;

	@Column(nullable = false)
	private String ownerId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String description;

	private UUID logoFileId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Visibility visibility;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Wiki() {
	}

	public Wiki(String id, String ownerId, String name, String description) {
		this.id = id;
		this.ownerId = ownerId;
		this.name = name;
		this.description = description;
		this.visibility = Visibility.PRIVATE;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	public static String randomId() {
		StringBuilder id = new StringBuilder(8);
		for (int i = 0; i < 8; i++) id.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
		return id.toString();
	}

	public String getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		touch();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
		touch();
	}

	public UUID getLogoFileId() {
		return logoFileId;
	}

	public void setLogoFileId(UUID logoFileId) {
		this.logoFileId = logoFileId;
		touch();
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public boolean isPublic() {
		return visibility == Visibility.PUBLIC;
	}

	public void setVisibility(Visibility visibility) {
		this.visibility = visibility;
		touch();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	private void touch() {
		updatedAt = Instant.now();
	}
}
