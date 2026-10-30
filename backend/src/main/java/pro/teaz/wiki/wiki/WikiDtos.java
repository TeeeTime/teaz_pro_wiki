package pro.teaz.wiki.wiki;

import java.util.UUID;

import pro.teaz.wiki.users.Users.UserView;

/** JSON shapes of the wiki API. */
public final class WikiDtos {

	private WikiDtos() {
	}

	/** How a wiki got into the caller's list. */
	public enum Relation {
		OWNER, EDITOR, VIEWER, SUBSCRIBER
	}

	/** An entry of the wiki switcher. */
	public record WikiSummary(String id, String name, UUID logoFileId, Wiki.Visibility visibility, Relation relation) {
	}

	/**
	 * @param level      what the caller may do, see WikiAccess
	 * @param subscribed only ever true for public wikis
	 */
	public record WikiDetail(String id, String name, String description, UUID logoFileId, Wiki.Visibility visibility,
			UserView owner, WikiAccess.Level level, boolean subscribed, boolean canComment) {
	}

	public record CreateWiki(String name, String description) {
	}

	/** Every field optional; {@code removeLogo} clears the logo. */
	public record UpdateWiki(String name, String description, UUID logoFileId, Boolean removeLogo,
			Wiki.Visibility visibility) {
	}

	public record MemberView(UserView user, WikiMember.Role role) {
	}

	public record MemberRequest(WikiMember.Role role) {
	}
}
