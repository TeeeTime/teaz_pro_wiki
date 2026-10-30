package pro.teaz.wiki.users;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Names of users for display. Keycloak is the source; this app keeps the
 * names it has seen (own logins via UserSync, search results when someone
 * is added to a wiki) so lists don't need a Keycloak call per row.
 */
@Component
public class Users {

	private final AppUserRepository users;

	public Users(AppUserRepository users) {
		this.users = users;
	}

	/** What the UI shows for a user. Unknown ids (never seen) show as "Unknown user". */
	public record UserView(String id, String username, String name) {
		static UserView unknown(String id) {
			return new UserView(id, "", "Unknown user");
		}
	}

	@Transactional
	public void remember(String id, String username, String displayName, String email) {
		users.findById(id).ifPresentOrElse(
				user -> {
					if (user.update(username, displayName, email)) users.save(user);
				},
				() -> users.save(new AppUser(id, username, displayName, email)));
	}

	public void remember(UserDirectory.DirectoryUser user) {
		remember(user.id(), user.username(), user.name(), user.email());
	}

	public UserView view(String id) {
		return users.findById(id).map(Users::view).orElseGet(() -> UserView.unknown(id));
	}

	public Map<String, UserView> views(Collection<String> ids) {
		Map<String, UserView> result = new HashMap<>();
		users.findAllById(new HashSet<>(ids)).forEach(user -> result.put(user.getId(), view(user)));
		ids.forEach(id -> result.computeIfAbsent(id, UserView::unknown));
		return result;
	}

	private static UserView view(AppUser user) {
		return new UserView(user.getId(), user.getUsername(), user.getDisplayName());
	}
}
