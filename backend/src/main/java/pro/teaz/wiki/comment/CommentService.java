package pro.teaz.wiki.comment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.wiki.node.NodeRepository;
import pro.teaz.wiki.users.Users;
import pro.teaz.wiki.wiki.WikiAccess;

/**
 * Comments under categories and pages: everyone signed in who can read the
 * wiki may comment. Replies are one level deep; a reply to a reply goes to
 * the same top-level comment. Authors edit and delete their own comments,
 * the wiki's owner may delete any.
 */
@Service
public class CommentService {

	static final int MAX_BODY = 5000;

	public record CommentView(UUID id, UUID parentId, Users.UserView author, String body, Instant createdAt,
			Instant editedAt, boolean deleted, boolean canEdit, boolean canDelete, List<CommentView> replies) {
	}

	public record CommentRequest(String body, UUID parentId) {
	}

	private final WikiAccess access;
	private final NodeRepository nodes;
	private final CommentRepository comments;
	private final Users users;

	public CommentService(WikiAccess access, NodeRepository nodes, CommentRepository comments, Users users) {
		this.access = access;
		this.nodes = nodes;
		this.comments = comments;
		this.users = users;
	}

	/** Top-level comments oldest first, each with its replies oldest first. */
	@Transactional(readOnly = true)
	public List<CommentView> list(String wikiId, UUID nodeId) {
		WikiAccess.Grant grant = access.read(wikiId);
		requireNode(wikiId, nodeId);
		List<Comment> all = comments.findByNodeIdOrderByCreatedAt(nodeId);
		Map<String, Users.UserView> names = users.views(all.stream().map(Comment::getAuthorId).toList());
		Map<UUID, List<CommentView>> replies = new LinkedHashMap<>();
		for (Comment comment : all) {
			if (comment.getParentId() != null) {
				replies.computeIfAbsent(comment.getParentId(), id -> new ArrayList<>())
						.add(view(comment, grant, names, List.of()));
			}
		}
		return all.stream()
				.filter(comment -> comment.getParentId() == null)
				.filter(comment -> !comment.isDeleted() || replies.containsKey(comment.getId()))
				.map(comment -> view(comment, grant, names, replies.getOrDefault(comment.getId(), List.of())))
				.toList();
	}

	@Transactional
	public List<CommentView> create(String wikiId, UUID nodeId, CommentRequest request) {
		WikiAccess.Grant grant = access.comment(wikiId);
		requireNode(wikiId, nodeId);
		UUID parentId = null;
		if (request.parentId() != null) {
			Comment parent = comments.findById(request.parentId())
					.filter(c -> c.getNodeId().equals(nodeId))
					.orElseThrow(() -> badRequest("The comment to reply to is gone."));
			parentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
		}
		comments.save(new Comment(nodeId, parentId, grant.userId().orElseThrow(), body(request.body())));
		return list(wikiId, nodeId);
	}

	@Transactional
	public List<CommentView> edit(String wikiId, UUID commentId, CommentRequest request) {
		WikiAccess.Grant grant = access.comment(wikiId);
		Comment comment = comment(wikiId, commentId);
		if (comment.isDeleted() || !comment.getAuthorId().equals(grant.userId().orElseThrow())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit a comment.");
		}
		comment.edit(body(request.body()));
		return list(wikiId, comment.getNodeId());
	}

	@Transactional
	public List<CommentView> delete(String wikiId, UUID commentId) {
		WikiAccess.Grant grant = access.comment(wikiId);
		Comment comment = comment(wikiId, commentId);
		if (!canDelete(comment, grant)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author or the wiki's owner can delete a comment.");
		}
		if (comment.getParentId() == null && comments.existsByParentId(comment.getId())) {
			comment.markDeleted();
		} else {
			comments.delete(comment);
		}
		return list(wikiId, comment.getNodeId());
	}

	private CommentView view(Comment comment, WikiAccess.Grant grant, Map<String, Users.UserView> names,
			List<CommentView> replies) {
		Optional<String> me = grant.userId();
		boolean mine = me.isPresent() && me.get().equals(comment.getAuthorId());
		return new CommentView(comment.getId(), comment.getParentId(),
				comment.isDeleted() ? null : names.get(comment.getAuthorId()), comment.getBody(), comment.getCreatedAt(),
				comment.getEditedAt(), comment.isDeleted(), mine && !comment.isDeleted(),
				!comment.isDeleted() && canDelete(comment, grant), replies);
	}

	private static boolean canDelete(Comment comment, WikiAccess.Grant grant) {
		if (grant.userId().isEmpty()) return false;
		return grant.level() == WikiAccess.Level.OWNER || comment.getAuthorId().equals(grant.userId().get());
	}

	private void requireNode(String wikiId, UUID nodeId) {
		nodes.findByIdAndWikiId(nodeId, wikiId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found."));
	}

	private Comment comment(String wikiId, UUID commentId) {
		Comment comment = comments.findById(commentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
		requireNode(wikiId, comment.getNodeId());
		return comment;
	}

	private static String body(String body) {
		String value = body == null ? "" : body.strip();
		if (value.isEmpty()) throw badRequest("Write something first.");
		if (value.length() > MAX_BODY) throw badRequest("Comments can be up to " + MAX_BODY + " characters.");
		return value;
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}
}
