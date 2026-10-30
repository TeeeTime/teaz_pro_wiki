package pro.teaz.wiki.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

	List<Comment> findByNodeIdOrderByCreatedAt(UUID nodeId);

	boolean existsByParentId(UUID parentId);
}
