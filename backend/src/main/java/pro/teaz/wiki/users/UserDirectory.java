package pro.teaz.wiki.users;

import java.util.List;
import java.util.Optional;

/**
 * All users of the realm, for adding people to a wiki: KeycloakUserDirectory
 * in prod, MockUserDirectory without an admin client secret (local dev, tests).
 */
public interface UserDirectory {

	record DirectoryUser(String id, String username, String name, String email) {
	}

	/** Up to {@code max} users whose username, name or email contains {@code query}. */
	List<DirectoryUser> search(String query, int max);

	Optional<DirectoryUser> find(String id);

	class DirectoryException extends RuntimeException {
		public DirectoryException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
