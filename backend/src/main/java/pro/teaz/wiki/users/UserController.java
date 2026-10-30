package pro.teaz.wiki.users;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pro.teaz.wiki.security.CurrentUser;

@RestController
@RequestMapping("/api")
public class UserController {

	private static final int MAX_RESULTS = 10;

	private final CurrentUser user;
	private final UserDirectory directory;

	public UserController(CurrentUser user, UserDirectory directory) {
		this.user = user;
		this.directory = directory;
	}

	/** People to add to a wiki: the realm's users matching {@code q}, without the caller. */
	@GetMapping("/users/search")
	public List<Users.UserView> search(@RequestParam String q) {
		String me = user.requireId();
		if (q.isBlank()) return List.of();
		return directory.search(q.strip(), MAX_RESULTS + 1).stream()
				.filter(found -> !found.id().equals(me))
				.limit(MAX_RESULTS)
				.map(found -> new Users.UserView(found.id(), found.username(), found.name()))
				.toList();
	}
}
