package pro.teaz.wiki.file;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An uploaded image: in a page or a wiki's logo. Never changes once stored. */
@Entity
public class StoredFile {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String wikiId;

	@Column(nullable = false)
	private String uploadedBy;

	@Column(nullable = false)
	private String mediaType;

	@Column(nullable = false)
	private long sizeBytes;

	private Integer width;

	private Integer height;

	@Column(nullable = false)
	private String storagePath;

	@Column(nullable = false)
	private Instant createdAt;

	protected StoredFile() {
	}

	public StoredFile(UUID id, String wikiId, String uploadedBy, String mediaType, long sizeBytes, Integer width,
			Integer height, String storagePath) {
		this.id = id;
		this.wikiId = wikiId;
		this.uploadedBy = uploadedBy;
		this.mediaType = mediaType;
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
		this.storagePath = storagePath;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getWikiId() {
		return wikiId;
	}

	public String getMediaType() {
		return mediaType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Integer getWidth() {
		return width;
	}

	public Integer getHeight() {
		return height;
	}

	public String getStoragePath() {
		return storagePath;
	}
}
