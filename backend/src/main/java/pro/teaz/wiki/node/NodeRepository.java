package pro.teaz.wiki.node;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NodeRepository extends JpaRepository<Node, UUID> {

	/** The whole structure of a wiki, without content. */
	@Query("select new pro.teaz.wiki.node.NodeDtos$TreeNode(n.id, n.parentId, n.kind, n.title, n.slug, n.position) "
			+ "from Node n where n.wikiId = :wikiId order by n.position")
	List<NodeDtos.TreeNode> tree(String wikiId);

	Optional<Node> findByIdAndWikiId(UUID id, String wikiId);

	Optional<Node> findByWikiIdAndParentKeyAndSlug(String wikiId, UUID parentKey, String slug);

	boolean existsByWikiIdAndParentKeyAndSlugAndIdNot(String wikiId, UUID parentKey, String slug, UUID id);

	List<Node> findByWikiIdAndParentKeyOrderByPosition(String wikiId, UUID parentKey);

	List<Node> findByParentId(UUID parentId);

	List<Node> findByWikiIdOrderByUpdatedAtDesc(String wikiId, Pageable page);

	/** {@code pattern} is lowercase and escaped with backslashes, see NodeService#search. */
	@Query("select n from Node n where n.wikiId = :wikiId "
			+ "and (lower(n.title) like :pattern escape '\\' or lower(n.plainText) like :pattern escape '\\')")
	List<Node> search(String wikiId, String pattern, Pageable page);
}
