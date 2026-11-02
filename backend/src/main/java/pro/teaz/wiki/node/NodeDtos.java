package pro.teaz.wiki.node;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pro.teaz.wiki.users.Users.UserView;

/** JSON shapes of the category/page API. */
public final class NodeDtos {

	private NodeDtos() {
	}

	/** One entry of the sidebar; the frontend builds the tree from parentId and position. */
	public record TreeNode(UUID id, UUID parentId, Node.Kind kind, String title, String slug, int position) {
	}

	/**
	 * @param path    slugs from the top-level category down to this node, the URL after the wiki id
	 * @param content Tiptap JSON
	 * @param version send back when saving; an outdated one answers 409
	 */
	public record NodeDetail(UUID id, UUID parentId, Node.Kind kind, String title, String slug, List<String> path,
			String content, int version, UserView createdBy, Instant createdAt, UserView updatedBy, Instant updatedAt) {
	}

	/** @param parentId null = top level, which only takes categories */
	public record CreateNode(UUID parentId, Node.Kind kind, String title, String content) {
	}

	/** {@code title} and {@code content} optional; {@code version} required. */
	public record UpdateNode(String title, String content, Integer version) {
	}

	/** @param position index among the new siblings; null or too large = last */
	public record MoveNode(UUID parentId, Integer position) {
	}

	public record NodeRef(UUID id, Node.Kind kind, String title, List<String> path, UserView updatedBy,
			Instant updatedAt) {
	}

	/** The wiki's landing page, built from the structure; not editable. */
	public record Overview(int categoryCount, int pageCount, List<NodeRef> recent) {
	}

	/** @param snippet text around the first match, or the start of the text for title-only matches */
	public record SearchHit(UUID id, Node.Kind kind, String title, List<String> path, String snippet) {
	}

	/** A page's plain text, e.g. for the MCP server's get_page tool. */
	public record TextDetail(String title, List<String> path, String text) {
	}
}
