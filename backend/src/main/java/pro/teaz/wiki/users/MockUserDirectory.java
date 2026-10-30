package pro.teaz.wiki.users;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Stand-in for Keycloak without an admin client secret: a few fixed users.
 * The frontend's mock auth offers the same ids to sign in as (mockAuth.ts),
 * and MockSecurityConfig only accepts these.
 */
public class MockUserDirectory implements UserDirectory {

	public static final List<DirectoryUser> USERS = List.of(
			new DirectoryUser("mock-jordan", "jlee", "Jordan Lee", "jordan.lee@example.com"),
			new DirectoryUser("mock-sam", "sam", "Sam Rivera", "sam.rivera@example.com"),
			new DirectoryUser("mock-alex", "alex", "Alex Kim", "alex.kim@example.com"),
			new DirectoryUser("mock-maria", "maria", "Maria Novak", "maria.novak@example.com"));

	public static Optional<DirectoryUser> byId(String id) {
		return USERS.stream().filter(user -> user.id().equals(id)).findFirst();
	}

	@Override
	public List<DirectoryUser> search(String query, int max) {
		String q = query.toLowerCase(Locale.ROOT);
		return USERS.stream()
				.filter(user -> (user.username() + " " + user.name() + " " + user.email()).toLowerCase(Locale.ROOT).contains(q))
				.limit(max)
				.toList();
	}

	@Override
	public Optional<DirectoryUser> find(String id) {
		return byId(id);
	}
}
