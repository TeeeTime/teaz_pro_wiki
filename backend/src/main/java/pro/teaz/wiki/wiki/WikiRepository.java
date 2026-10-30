package pro.teaz.wiki.wiki;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiRepository extends JpaRepository<Wiki, String> {

	List<Wiki> findByOwnerId(String ownerId);
}
