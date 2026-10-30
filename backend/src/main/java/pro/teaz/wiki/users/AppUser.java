package pro.teaz.wiki.users;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A Keycloak user's name as last seen, see {@link Users}. */
@Entity
public class AppUser {

	@Id
	private String id;

	@Column(nullable = false)
	private String username;

	@Column(nullable = false)
	private String displayName;

	private String email;

	@Column(nullable = false)
	private Instant updatedAt;

	protected AppUser() {
	}

	public AppUser(String id, String username, String displayName, String email) {
		this.id = id;
		update(username, displayName, email);
	}

	/** @return whether anything changed */
	public boolean update(String username, String displayName, String email) {
		if (Objects.equals(this.username, username) && Objects.equals(this.displayName, displayName)
				&& Objects.equals(this.email, email)) {
			return false;
		}
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.updatedAt = Instant.now();
		return true;
	}

	public String getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getEmail() {
		return email;
	}
}
