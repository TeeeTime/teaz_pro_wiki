package pro.teaz.wiki.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.wiki.users.Users;
import pro.teaz.wiki.wiki.WikiAccess;

/**
 * Categories and pages. The structure is at most four levels deep:
 *
 * <ol start="0">
 * <li>categories only</li>
 * <li>pages or sub-categories, in a category</li>
 * <li>pages or sub-categories, in a sub-category</li>
 * <li>pages only, in a sub-category of a sub-category</li>
 * </ol>
 *
 * Names are unique among siblings by their slug, since siblings share the URL
 * prefix: wiki.teaz.pro/{wiki}/{category}/{sub-category or page}/{sub-category or page}/{page}.
 */
@Service
public class NodeService {

	static final int MAX_TITLE = 120;
	static final int MAX_LEVEL = 3;
	static final int RECENT = 8;
	static final int MAX_HITS = 30;
	static final int SNIPPET_RADIUS = 70;

	private final WikiAccess access;
	private final NodeRepository nodes;
	private final ContentText contentText;
	private final Users users;

	public NodeService(WikiAccess access, NodeRepository nodes, ContentText contentText, Users users) {
		this.access = access;
		this.nodes = nodes;
		this.contentText = contentText;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<NodeDtos.TreeNode> tree(String wikiId) {
		access.read(wikiId);
		return nodes.tree(wikiId);
	}

	@Transactional(readOnly = true)
	public NodeDtos.NodeDetail byPath(String wikiId, String path) {
		access.read(wikiId);
		List<String> slugs = Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();
		if (slugs.isEmpty() || slugs.size() > MAX_LEVEL + 1) throw notFound();
		Node node = null;
		for (String slug : slugs) {
			UUID parentKey = node == null ? Node.ROOT_KEY : node.getId();
			node = nodes.findByWikiIdAndParentKeyAndSlug(wikiId, parentKey, slug.toLowerCase(Locale.ROOT))
					.orElseThrow(NodeService::notFound);
		}
		return detail(node);
	}

	@Transactional(readOnly = true)
	public NodeDtos.NodeDetail get(String wikiId, UUID id) {
		access.read(wikiId);
		return detail(node(wikiId, id));
	}

	@Transactional
	public NodeDtos.NodeDetail create(String wikiId, NodeDtos.CreateNode request) {
		String userId = access.edit(wikiId).userId().orElseThrow();
		if (request.kind() == null) throw badRequest("Choose category or page.");
		int level = request.parentId() == null ? 0 : level(node(wikiId, request.parentId())) + 1;
		if (request.parentId() != null && node(wikiId, request.parentId()).getKind() != Node.Kind.CATEGORY) {
			throw badRequest("Pages can't contain other pages.");
		}
		checkPlacement(request.kind(), level);
		String title = title(request.title());
		String slug = slug(title);
		UUID parentKey = request.parentId() == null ? Node.ROOT_KEY : request.parentId();
		checkUnique(wikiId, parentKey, slug, null, title);
		List<Node> siblings = nodes.findByWikiIdAndParentKeyOrderByPosition(wikiId, parentKey);
		int position = siblings.isEmpty() ? 0 : siblings.getLast().getPosition() + 1;
		Node node = new Node(wikiId, request.parentId(), request.kind(), title, slug, position, userId);
		if (request.content() != null) node.setContent(request.content(), contentText.check(request.content()));
		nodes.save(node);
		return detail(node);
	}

	@Transactional
	public NodeDtos.NodeDetail update(String wikiId, UUID id, NodeDtos.UpdateNode request) {
		String userId = access.edit(wikiId).userId().orElseThrow();
		Node node = node(wikiId, id);
		if (request.version() == null || request.version() != node.getVersion()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Someone else changed this in the meantime. Reload to see their version.");
		}
		if (request.title() != null) {
			String title = title(request.title());
			String slug = slug(title);
			checkUnique(wikiId, parentKey(node), slug, node.getId(), title);
			node.rename(title, slug);
		}
		if (request.content() != null) node.setContent(request.content(), contentText.check(request.content()));
		node.touch(userId);
		nodes.saveAndFlush(node);
		return detail(node);
	}

	/** Moves a node (with everything in it) to another parent and/or position, if the depth rules allow it. */
	@Transactional
	public List<NodeDtos.TreeNode> move(String wikiId, UUID id, NodeDtos.MoveNode request) {
		access.edit(wikiId);
		Node node = node(wikiId, id);
		int level = 0;
		if (request.parentId() != null) {
			Node parent = node(wikiId, request.parentId());
			if (parent.getKind() != Node.Kind.CATEGORY) throw badRequest("Pages can't contain other pages.");
			for (Node ancestor = parent; ancestor != null; ancestor = parentOf(ancestor)) {
				if (ancestor.getId().equals(node.getId())) throw badRequest("A category can't move into itself.");
			}
			level = level(parent) + 1;
		}
		checkSubtree(node, level);
		UUID parentKey = request.parentId() == null ? Node.ROOT_KEY : request.parentId();
		checkUnique(wikiId, parentKey, node.getSlug(), node.getId(), node.getTitle());

		List<Node> siblings = new ArrayList<>(nodes.findByWikiIdAndParentKeyOrderByPosition(wikiId, parentKey));
		siblings.removeIf(sibling -> sibling.getId().equals(node.getId()));
		int position = request.position() == null ? siblings.size()
				: Math.max(0, Math.min(request.position(), siblings.size()));
		siblings.add(position, node);
		node.setParent(request.parentId(), position);
		for (int i = 0; i < siblings.size(); i++) siblings.get(i).setPosition(i);
		nodes.saveAll(siblings);
		nodes.flush();
		return nodes.tree(wikiId);
	}

	/** A category with content needs {@code recursive}; its pages and comments go with it. */
	@Transactional
	public void delete(String wikiId, UUID id, boolean recursive) {
		access.edit(wikiId);
		Node node = node(wikiId, id);
		if (!recursive && !nodes.findByParentId(node.getId()).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "The category isn't empty.");
		}
		nodes.delete(node);
	}

	@Transactional(readOnly = true)
	public NodeDtos.Overview overview(String wikiId) {
		access.read(wikiId);
		List<NodeDtos.TreeNode> tree = nodes.tree(wikiId);
		Map<UUID, NodeDtos.TreeNode> byId = byId(tree);
		int categories = (int) tree.stream().filter(n -> n.kind() == Node.Kind.CATEGORY).count();
		List<Node> recent = nodes.findByWikiIdOrderByUpdatedAtDesc(wikiId, PageRequest.of(0, RECENT));
		Map<String, Users.UserView> names = users.views(recent.stream().map(Node::getUpdatedBy).toList());
		return new NodeDtos.Overview(categories, tree.size() - categories, recent.stream()
				.map(n -> new NodeDtos.NodeRef(n.getId(), n.getKind(), n.getTitle(), path(n.getId(), byId),
						names.get(n.getUpdatedBy()), n.getUpdatedAt()))
				.toList());
	}

	/**
	 * Case-insensitive; with several words, every word has to occur (in the
	 * title or the text). Title matches come first.
	 */
	@Transactional(readOnly = true)
	public List<NodeDtos.SearchHit> search(String wikiId, String query) {
		access.read(wikiId);
		List<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+")).filter(t -> !t.isBlank()).toList();
		if (terms.isEmpty()) return List.of();
		String longest = terms.stream().max((a, b) -> a.length() - b.length()).orElseThrow();
		String pattern = "%" + longest.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		Map<UUID, NodeDtos.TreeNode> byId = byId(nodes.tree(wikiId));
		List<Node> found = new ArrayList<>(nodes.search(wikiId, pattern, PageRequest.of(0, 200)).stream()
				.filter(n -> terms.stream().allMatch(t -> (n.getTitle() + "\n" + n.getPlainText()).toLowerCase(Locale.ROOT).contains(t)))
				.toList());
		found.sort((a, b) -> Integer.compare(titleScore(b, terms), titleScore(a, terms)));
		return found.stream()
				.limit(MAX_HITS)
				.map(n -> new NodeDtos.SearchHit(n.getId(), n.getKind(), n.getTitle(), path(n.getId(), byId),
						snippet(n.getPlainText(), terms)))
				.toList();
	}

	private static int titleScore(Node node, List<String> terms) {
		String title = node.getTitle().toLowerCase(Locale.ROOT);
		return (int) terms.stream().filter(title::contains).count();
	}

	static String snippet(String text, List<String> terms) {
		String lower = text.toLowerCase(Locale.ROOT);
		int at = terms.stream().mapToInt(lower::indexOf).filter(i -> i >= 0).min().orElse(0);
		int from = Math.max(0, at - SNIPPET_RADIUS);
		int to = Math.min(text.length(), at + SNIPPET_RADIUS * 2);
		String snippet = text.substring(from, to).replaceAll("\\s+", " ").strip();
		return (from > 0 ? "…" : "") + snippet + (to < text.length() ? "…" : "");
	}

	NodeDtos.NodeDetail detail(Node node) {
		Map<String, Users.UserView> names = users.views(List.of(node.getCreatedBy(), node.getUpdatedBy()));
		return new NodeDtos.NodeDetail(node.getId(), node.getParentId(), node.getKind(), node.getTitle(), node.getSlug(),
				path(node), node.getContent(), node.getVersion(), names.get(node.getCreatedBy()), node.getCreatedAt(),
				names.get(node.getUpdatedBy()), node.getUpdatedAt());
	}

	/** Slugs from the top level down to {@code node}. */
	public List<String> path(Node node) {
		List<String> path = new ArrayList<>();
		for (Node current = node; current != null; current = parentOf(current)) path.add(current.getSlug());
		Collections.reverse(path);
		return path;
	}

	static List<String> path(UUID id, Map<UUID, NodeDtos.TreeNode> byId) {
		List<String> path = new ArrayList<>();
		for (NodeDtos.TreeNode current = byId.get(id); current != null; current = current.parentId() == null ? null
				: byId.get(current.parentId())) {
			path.add(current.slug());
		}
		Collections.reverse(path);
		return path;
	}

	private static Map<UUID, NodeDtos.TreeNode> byId(List<NodeDtos.TreeNode> tree) {
		Map<UUID, NodeDtos.TreeNode> byId = new HashMap<>();
		tree.forEach(n -> byId.put(n.id(), n));
		return byId;
	}

	private Node node(String wikiId, UUID id) {
		return nodes.findByIdAndWikiId(id, wikiId).orElseThrow(NodeService::notFound);
	}

	private Node parentOf(Node node) {
		return node.getParentId() == null ? null : nodes.findById(node.getParentId()).orElse(null);
	}

	private int level(Node node) {
		int level = 0;
		for (Node current = parentOf(node); current != null; current = parentOf(current)) level++;
		return level;
	}

	private static UUID parentKey(Node node) {
		return node.getParentId() == null ? Node.ROOT_KEY : node.getParentId();
	}

	/** Top level: categories only. Fourth level: pages only. Nothing deeper. */
	static void checkPlacement(Node.Kind kind, int level) {
		if (level > MAX_LEVEL) throw badRequest("The structure is at most four levels deep.");
		if (level == 0 && kind != Node.Kind.CATEGORY) throw badRequest("The top level only holds categories.");
		if (level == MAX_LEVEL && kind != Node.Kind.PAGE) {
			throw badRequest("Sub-categories at this level can only hold pages, not further categories.");
		}
	}

	private void checkSubtree(Node node, int level) {
		checkPlacement(node.getKind(), level);
		for (Node child : nodes.findByParentId(node.getId())) checkSubtree(child, level + 1);
	}

	private void checkUnique(String wikiId, UUID parentKey, String slug, UUID self, String title) {
		boolean taken = nodes.existsByWikiIdAndParentKeyAndSlugAndIdNot(wikiId, parentKey, slug,
				self == null ? Node.ROOT_KEY : self);
		if (taken) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"\"" + title + "\" collides with an existing name here (both would use the address \"" + slug + "\").");
		}
	}

	private static String title(String title) {
		String value = title == null ? "" : title.strip();
		if (value.isEmpty()) throw badRequest("Give it a name.");
		if (value.length() > MAX_TITLE) throw badRequest("Names can be up to " + MAX_TITLE + " characters.");
		return value;
	}

	private static String slug(String title) {
		String slug = Slugs.of(title);
		if (slug.isEmpty()) throw badRequest("Use at least one letter or digit in the name.");
		return slug;
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
	}
}
