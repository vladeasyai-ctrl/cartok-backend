package pl.cartok.activity;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InteractionRepository extends JpaRepository<InteractionEntity, InteractionEntity.Key> {

	@Query("""
			select interaction.listingId from InteractionEntity interaction
			where interaction.userId = :userId and interaction.likedAt is not null and interaction.listingId in :listingIds
			""")
	List<String> likedAmong(String userId, Collection<String> listingIds);

	/** Давно виденные первыми: ими лента добивается, когда непросмотренное кончилось. */
	@Query("""
			select interaction.listingId from InteractionEntity interaction
			where interaction.userId = :userId and interaction.seenAt is not null
			order by interaction.lastEventAt asc
			""")
	List<String> seenOldestFirst(String userId, Pageable page);
}
