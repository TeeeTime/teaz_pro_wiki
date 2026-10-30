package pro.teaz.wiki.wiki;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.wiki.file.FileStorage;
import pro.teaz.wiki.file.StoredFile;
import pro.teaz.wiki.file.StoredFileRepository;
import pro.teaz.wiki.security.CurrentUser;
import pro.teaz.wiki.users.UserDirectory;
import pro.teaz.wiki.users.Users;
import pro.teaz.wiki.wiki.WikiDtos.Relation;

@Service
public class WikiService {

	static final int MAX_NAME = 80;
	static final int MAX_DESCRIPTION = 2000;

	private final CurrentUser user;
	private final WikiAccess access;
	private final WikiRepository wikis;
	private final WikiMemberRepository members;
	private final WikiSubscriptionRepository subscriptions;
	private final StoredFileRepository files;
	private final FileStorage storage;
	private final UserDirectory directory;
	private final Users users;

	public WikiService(CurrentUser user, WikiAccess access, WikiRepository wikis, WikiMemberRepository members,
			WikiSubscriptionRepository subscriptions, StoredFileRepository files, FileStorage storage,
			UserDirectory directory, Users users) {
		this.user = user;
		this.access = access;
		this.wikis = wikis;
		this.members = members;
		this.subscriptions = subscriptions;
		this.files = files;
		this.storage = storage;
		this.directory = directory;
		this.users = users;
	}

	/** The caller's wikis: own, shared with them, and subscribed public ones; by name. Empty for guests. */
	@Transactional(readOnly = true)
	public List<WikiDtos.WikiSummary> list() {
		Optional<String> me = user.id();
		if (me.isEmpty()) return List.of();
		Map<String, WikiDtos.WikiSummary> result = new LinkedHashMap<>();
		for (Wiki wiki : wikis.findByOwnerId(me.get())) result.put(wiki.getId(), summary(wiki, Relation.OWNER));
		for (WikiMember member : members.findByUserId(me.get())) {
			wikis.findById(member.getWikiId()).ifPresent(wiki -> result.putIfAbsent(wiki.getId(), summary(wiki,
					member.getRole() == WikiMember.Role.EDITOR ? Relation.EDITOR : Relation.VIEWER)));
		}
		for (WikiSubscription subscription : subscriptions.findByUserId(me.get())) {
			wikis.findById(subscription.getWikiId())
					.filter(Wiki::isPublic)
					.ifPresent(wiki -> result.putIfAbsent(wiki.getId(), summary(wiki, Relation.SUBSCRIBER)));
		}
		List<WikiDtos.WikiSummary> list = new ArrayList<>(result.values());
		list.sort(Comparator.comparing((WikiDtos.WikiSummary w) -> w.name().toLowerCase(Locale.ROOT)));
		return list;
	}

	@Transactional
	public WikiDtos.WikiDetail create(WikiDtos.CreateWiki request) {
		String owner = user.requireId();
		String id;
		do {
			id = Wiki.randomId();
		} while (wikis.existsById(id));
		Wiki wiki = new Wiki(id, owner, name(request.name()), description(request.description()));
		wikis.save(wiki);
		return detail(new WikiAccess.Grant(wiki, WikiAccess.Level.OWNER, Optional.of(owner)));
	}

	@Transactional(readOnly = true)
	public WikiDtos.WikiDetail detail(String wikiId) {
		return detail(access.read(wikiId));
	}

	@Transactional
	public WikiDtos.WikiDetail update(String wikiId, WikiDtos.UpdateWiki request) {
		WikiAccess.Grant grant = access.own(wikiId);
		Wiki wiki = grant.wiki();
		if (request.name() != null) wiki.setName(name(request.name()));
		if (request.description() != null) wiki.setDescription(description(request.description()));
		if (Boolean.TRUE.equals(request.removeLogo())) {
			wiki.setLogoFileId(null);
		} else if (request.logoFileId() != null) {
			StoredFile logo = files.findById(request.logoFileId())
					.filter(file -> file.getWikiId().equals(wikiId))
					.orElseThrow(() -> badRequest("Upload the logo to this wiki first."));
			wiki.setLogoFileId(logo.getId());
		}
		if (request.visibility() != null) wiki.setVisibility(request.visibility());
		wikis.save(wiki);
		return detail(grant);
	}

	/** Everything goes: categories, pages and comments (foreign keys), members, subscriptions, images. */
	@Transactional
	public void delete(String wikiId) {
		Wiki wiki = access.own(wikiId).wiki();
		wiki.setLogoFileId(null);
		wikis.saveAndFlush(wiki);
		files.deleteByWikiId(wikiId);
		wikis.delete(wiki);
		storage.deleteWiki(wikiId);
	}

	@Transactional
	public WikiDtos.WikiDetail subscribe(String wikiId) {
		WikiAccess.Grant grant = access.comment(wikiId);
		if (!grant.wiki().isPublic()) throw badRequest("Only public wikis can be subscribed to.");
		String me = grant.userId().orElseThrow();
		if (!subscriptions.existsById(new WikiSubscription.Key(wikiId, me))) {
			subscriptions.save(new WikiSubscription(wikiId, me));
		}
		return detail(grant);
	}

	@Transactional
	public WikiDtos.WikiDetail unsubscribe(String wikiId) {
		WikiAccess.Grant grant = access.comment(wikiId);
		subscriptions.deleteById(new WikiSubscription.Key(wikiId, grant.userId().orElseThrow()));
		return detail(grant);
	}

	@Transactional(readOnly = true)
	public List<WikiDtos.MemberView> members(String wikiId) {
		access.own(wikiId);
		List<WikiMember> list = members.findByWikiIdOrderByAddedAt(wikiId);
		Map<String, Users.UserView> views = users.views(list.stream().map(WikiMember::getUserId).toList());
		return list.stream().map(member -> new WikiDtos.MemberView(views.get(member.getUserId()), member.getRole())).toList();
	}

	/** Adds someone (found by the user search) or changes their role. */
	@Transactional
	public List<WikiDtos.MemberView> putMember(String wikiId, String userId, WikiDtos.MemberRequest request) {
		Wiki wiki = access.own(wikiId).wiki();
		if (request.role() == null) throw badRequest("Choose a role.");
		if (wiki.getOwnerId().equals(userId)) throw badRequest("The owner can't be added as a member.");
		members.findById(new WikiMember.Key(wikiId, userId)).ifPresentOrElse(
				member -> member.setRole(request.role()),
				() -> {
					UserDirectory.DirectoryUser found = directory.find(userId)
							.orElseThrow(() -> badRequest("No such user."));
					users.remember(found);
					members.save(new WikiMember(wikiId, userId, request.role()));
				});
		return members(wikiId);
	}

	@Transactional
	public List<WikiDtos.MemberView> removeMember(String wikiId, String userId) {
		access.own(wikiId);
		members.deleteById(new WikiMember.Key(wikiId, userId));
		return members(wikiId);
	}

	WikiDtos.WikiDetail detail(WikiAccess.Grant grant) {
		Wiki wiki = grant.wiki();
		boolean subscribed = wiki.isPublic() && grant.userId()
				.map(id -> subscriptions.existsById(new WikiSubscription.Key(wiki.getId(), id)))
				.orElse(false);
		return new WikiDtos.WikiDetail(wiki.getId(), wiki.getName(), wiki.getDescription(), wiki.getLogoFileId(),
				wiki.getVisibility(), users.view(wiki.getOwnerId()), grant.level(), subscribed, grant.canComment());
	}

	private static WikiDtos.WikiSummary summary(Wiki wiki, Relation relation) {
		return new WikiDtos.WikiSummary(wiki.getId(), wiki.getName(), wiki.getLogoFileId(), wiki.getVisibility(), relation);
	}

	private static String name(String name) {
		String value = name == null ? "" : name.strip();
		if (value.isEmpty()) throw badRequest("Give the wiki a name.");
		if (value.length() > MAX_NAME) throw badRequest("Names can be up to " + MAX_NAME + " characters.");
		return value;
	}

	private static String description(String description) {
		String value = description == null ? "" : description.strip();
		if (value.length() > MAX_DESCRIPTION) {
			throw badRequest("Descriptions can be up to " + MAX_DESCRIPTION + " characters.");
		}
		return value;
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}
}
