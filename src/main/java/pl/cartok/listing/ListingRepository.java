package pl.cartok.listing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ListingRepository extends JpaRepository<ListingEntity, String> {

	Optional<ListingEntity> findBySourceAndExternalId(String source, String externalId);

	@EntityGraph(attributePaths = "photos")
	List<ListingEntity> findByStatus(String status, Pageable pageable);

	@EntityGraph(attributePaths = "photos")
	List<ListingEntity> findByStatusAndIdNotIn(String status, Collection<String> ids, Pageable pageable);

	@EntityGraph(attributePaths = "photos")
	List<ListingEntity> findByStatusAndIdIn(String status, Collection<String> ids);

	@EntityGraph(attributePaths = "photos")
	Optional<ListingEntity> findByIdAndStatus(String id, String status);

	@Query("""
			select new pl.cartok.listing.SlugActivity(listing.searchSlug, max(listing.lastSeenAt))
			from ListingEntity listing
			where listing.source = 'otomoto'
			group by listing.searchSlug
			""")
	List<SlugActivity> lastSeenBySlug();
}
