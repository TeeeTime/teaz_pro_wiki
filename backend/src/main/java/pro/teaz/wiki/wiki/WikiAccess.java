package pro.teaz.wiki.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.wiki.security.CurrentUser;

/**
 * What the current caller may do in a wiki, see the permission table in the
 * README. A wiki the caller can't read answers 404, same as one that doesn't
 * exist; reading but not being allowed to change something answers 403.
 */
@Component
public class WikiAccess {

	public enum Level {
		NONE,
		/** A public wiki, as a guest or as a signed-in non-member. */
		READER,
		VIEWER,
		EDITOR,
		OWNER;

		public boolean canRead() {
			return this != NONE;
		}

		public boolean canEdit() {
			return this == EDITOR || this == OWNER;
		}
	}

	/** A wiki together with the caller's level in it. */
	public record Grant(Wiki wiki, Level level, Optional<String> userId) {

		public boolean canComment() {
			return level.canRead() && userId.isPresent();
		}
	}

	private final CurrentUser user;
	private final WikiRepository wikis;
	private final WikiMemberRepository members;

	public WikiAccess(CurrentUser user, WikiRepository wikis, WikiMemberRepository members) {
		this.user = user;
		this.wikis = wikis;
		this.members = members;
	}

	public Level level(Wiki wiki, Optional<String> userId) {
		if (userId.isPresent()) {
			String id = userId.get();
			if (wiki.getOwnerId().equals(id)) return Level.OWNER;
			Optional<WikiMember> member = members.findById(new WikiMember.Key(wiki.getId(), id));
			if (member.isPresent()) {
				return member.get().getRole() == WikiMember.Role.EDITOR ? Level.EDITOR : Level.VIEWER;
			}
		}
		return wiki.isPublic() ? Level.READER : Level.NONE;
	}

	/** Read access, also for guests on public wikis. */
	public Grant read(String wikiId) {
		Optional<String> userId = user.id();
		Wiki wiki = wikis.findById(wikiId).orElseThrow(WikiAccess::notFound);
		Level level = level(wiki, userId);
		if (!level.canRead()) throw notFound();
		return new Grant(wiki, level, userId);
	}

	/** Signed in and allowed to read: comments. */
	public Grant comment(String wikiId) {
		user.requireId();
		return read(wikiId);
	}

	public Grant edit(String wikiId) {
		user.requireId();
		Grant grant = read(wikiId);
		if (!grant.level().canEdit()) throw forbidden("Only editors can change this wiki.");
		return grant;
	}

	public Grant own(String wikiId) {
		user.requireId();
		Grant grant = read(wikiId);
		if (grant.level() != Level.OWNER) throw forbidden("Only the owner can change the wiki's settings.");
		return grant;
	}

	/**
	 * Every public wiki, plus the caller's own and shared ones (not merely
	 * subscribed-to public ones, unlike {@code WikiService.list()}). This is
	 * the complete set an MCP-style cross-wiki search may look at; reading any
	 * one of them still goes through {@link #read(String)} as usual.
	 */
	public List<Wiki> readableWikis() {
		Map<String, Wiki> result = new LinkedHashMap<>();
		for (Wiki wiki : wikis.findByVisibility(Wiki.Visibility.PUBLIC)) result.put(wiki.getId(), wiki);
		user.id().ifPresent(id -> {
			for (Wiki wiki : wikis.findByOwnerId(id)) result.put(wiki.getId(), wiki);
			for (WikiMember member : members.findByUserId(id)) {
				wikis.findById(member.getWikiId()).ifPresent(wiki -> result.put(wiki.getId(), wiki));
			}
		});
		return new ArrayList<>(result.values());
	}

	public static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Wiki not found.");
	}

	private static ResponseStatusException forbidden(String detail) {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, detail);
	}
}
