package pl.cartok.preference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PreferenceRepository extends JpaRepository<PreferenceEntity, Long> {

	List<PreferenceEntity> findByUserIdOrderByPriorityAscIdAsc(String userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PreferenceEntity preference where preference.userId = :userId")
	void deleteByUserId(String userId);
}
