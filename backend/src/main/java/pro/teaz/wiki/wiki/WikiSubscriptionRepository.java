package pro.teaz.wiki.wiki;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WikiSubscriptionRepository extends JpaRepository<WikiSubscription, WikiSubscription.Key> {

	List<WikiSubscription> findByUserId(String userId);

	@Modifying
	@Query("delete from WikiSubscription s where s.wikiId = :wikiId")
	void deleteByWikiId(String wikiId);
}
