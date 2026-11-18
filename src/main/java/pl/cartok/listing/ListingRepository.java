package pl.cartok.listing;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ListingRepository extends JpaRepository<ListingEntity, String>, JpaSpecificationExecutor<ListingEntity> {

	Optional<ListingEntity> findBySourceAndExternalId(String source, String externalId);

	@EntityGraph(attributePaths = "photos")
	List<ListingEntity> findByStatus(String status, Pageable pageable);

	@EntityGraph(attributePaths = "photos")
	Optional<ListingEntity> findByIdAndStatus(String id, String status);

	@Query("""
			select listing.externalId from ListingEntity listing
			where listing.source = :source and listing.externalId in :externalIds
			""")
	List<String> findExternalIds(String source, Collection<String> externalIds);

	@Query("""
			select listing.externalId from ListingEntity listing
			where listing.source = :source and listing.externalId in :externalIds and listing.detailed = false
			""")
	List<String> findUndetailedExternalIds(String source, Collection<String> externalIds);

	@Query("""
			select new pl.cartok.listing.ThinListing(listing.externalId, listing.sourceUrl)
			from ListingEntity listing
			where listing.source = 'otomoto' and listing.detailed = false
				and listing.makeSlug = :makeSlug and listing.sourceUrl <> ''
			order by listing.publishedAt desc nulls last
			""")
	List<ThinListing> findUndetailed(String makeSlug, Pageable pageable);

	@Modifying
	@Query("""
			update ListingEntity listing set listing.lastSeenAt = :seenAt, listing.searchSlug = :searchSlug
			where listing.source = :source and listing.externalId in :externalIds
			""")
	void touch(String source, Collection<String> externalIds, String searchSlug, OffsetDateTime seenAt);

	@Query("""
			select new pl.cartok.listing.SlugActivity(listing.searchSlug, max(listing.lastSeenAt))
			from ListingEntity listing
			where listing.source = 'otomoto'
			group by listing.searchSlug
			""")
	List<SlugActivity> lastSeenBySlug();
}
