package pro.teaz.wiki.file;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

	@Modifying
	@Query("delete from StoredFile f where f.wikiId = :wikiId")
	void deleteByWikiId(String wikiId);
}
