package pro.teaz.wiki.wiki;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

/** A public wiki in someone's wiki list. Ignored while the wiki is private. */
@Entity
@IdClass(WikiSubscription.Key.class)
public class WikiSubscription {

	public record Key(String wikiId, String userId) implements Serializable {
		public Key() {
			this(null, null);
		}
	}

	@Id
	private String wikiId;

	@Id
	private String userId;

	@Column(nullable = false)
	private Instant createdAt;

	protected WikiSubscription() {
	}

	public WikiSubscription(String wikiId, String userId) {
		this.wikiId = wikiId;
		this.userId = userId;
		this.createdAt = Instant.now();
	}

	public String getWikiId() {
		return wikiId;
	}

	public String getUserId() {
		return userId;
	}
}
