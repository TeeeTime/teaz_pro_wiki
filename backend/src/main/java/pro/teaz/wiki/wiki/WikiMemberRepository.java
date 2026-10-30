package pro.teaz.wiki.wiki;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WikiMemberRepository extends JpaRepository<WikiMember, WikiMember.Key> {

	List<WikiMember> findByWikiIdOrderByAddedAt(String wikiId);

	List<WikiMember> findByUserId(String userId);

	@Modifying
	@Query("delete from WikiMember m where m.wikiId = :wikiId")
	void deleteByWikiId(String wikiId);
}
