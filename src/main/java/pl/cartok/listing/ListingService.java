package pl.cartok.listing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cartok.feed.ListingView;
import pl.cartok.ingest.ParsedListing;

@Service
public class ListingService {

	private final ListingRepository listings;

	public ListingService(ListingRepository listings) {
		this.listings = listings;
	}

	@Transactional
	public void save(ParsedListing parsed, String searchSlug, OffsetDateTime seenAt) {
		ListingEntity entity = listings.findBySourceAndExternalId("otomoto", parsed.externalId())
				.orElseGet(ListingEntity::new);
		entity.setId(parsed.id());
		entity.setSource("otomoto");
		entity.setExternalId(parsed.externalId());
		entity.setTitle(blank(parsed.title(), parsed.make() + " " + parsed.model()));
		entity.setMake(blank(parsed.make(), ""));
		entity.setModel(blank(parsed.model(), ""));
		entity.setYear(parsed.year());
		entity.setPriceAmount(parsed.priceAmount() == null || parsed.priceAmount() == 0 ? null : parsed.priceAmount());
		entity.setCurrency(blank(parsed.currency(), "PLN"));
		entity.setMileageKm(parsed.mileageKm());
		entity.setFuel(blank(parsed.fuel(), ""));
		entity.setTransmission(blank(parsed.transmission(), ""));
		entity.setLocation(blank(parsed.location(), ""));
		entity.setDescription(blank(parsed.description(), ""));
		entity.setSellerId(blank(parsed.sellerId(), parsed.externalId()));
		entity.setSellerName(sellerName(parsed));
		entity.setSellerKind(blank(parsed.sellerKind(), "private"));
		entity.setSearchSlug(searchSlug);
		entity.setSourceUrl(parsed.sourceUrl());
		entity.setStatus(blank(parsed.status(), "active"));
		entity.setPublishedAt(parsed.publishedAt() == null ? null : parsed.publishedAt().atOffset(ZoneOffset.UTC));
		entity.setLastSeenAt(seenAt);
		entity.getPhotos().clear();
		ListingEntity saved = listings.saveAndFlush(entity);
		int position = 0;
		for (ParsedListing.Photo photo : parsed.photos()) {
			ListingPhotoEntity row = new ListingPhotoEntity();
			row.setListing(saved);
			row.setPosition(position++);
			row.setFileId(photo.fileId());
			row.setUrl(photo.url());
			saved.getPhotos().add(row);
		}
		listings.save(saved);
	}

	@Transactional(readOnly = true)
	public Map<String, OffsetDateTime> lastSeenBySlug() {
		return listings.lastSeenBySlug().stream()
				.collect(Collectors.toMap(SlugActivity::slug, SlugActivity::lastSeenAt));
	}

	@Transactional(readOnly = true)
	public List<ListingView> feed(int limit) {
		return listings.findByStatus("active", feedPage(limit)).stream().map(ListingService::toView).toList();
	}

	/**
	 * Непросмотренные идут первыми. Виденные не выкидываются, а добивают ленту до limit
	 * в порядке просмотра, чтобы при коротком каталоге она не опустела.
	 */
	@Transactional(readOnly = true)
	public List<ListingView> feed(int limit, List<String> seenIds) {
		if (seenIds.isEmpty()) return feed(limit);
		List<ListingView> result = new ArrayList<>(limit);
		listings.findByStatusAndIdNotIn("active", seenIds, feedPage(limit)).stream()
				.map(ListingService::toView)
				.forEach(result::add);
		if (result.size() >= limit) return result;
		Map<String, ListingEntity> seen = listings.findByStatusAndIdIn("active", seenIds).stream()
				.collect(Collectors.toMap(ListingEntity::getId, Function.identity()));
		for (String id : seenIds) {
			if (result.size() >= limit) break;
			ListingEntity entity = seen.get(id);
			if (entity != null) result.add(toView(entity));
		}
		return result;
	}

	private static PageRequest feedPage(int limit) {
		return PageRequest.of(0, limit, Sort.by(
				Sort.Order.desc("publishedAt").with(Sort.NullHandling.NULLS_LAST),
				Sort.Order.desc("id")
		));
	}

	@Transactional(readOnly = true)
	public ListingView findActive(String id) {
		return listings.findByIdAndStatus(id, "active").map(ListingService::toView).orElse(null);
	}

	private static ListingView toView(ListingEntity entity) {
		List<String> images = entity.getPhotos().stream()
				.sorted(Comparator.comparingInt(ListingPhotoEntity::getPosition))
				.map(ListingPhotoEntity::getUrl)
				.toList();
		return new ListingView(
				entity.getId(),
				entity.getTitle(),
				entity.getMake(),
				entity.getModel(),
				entity.getYear() == null ? 0 : entity.getYear(),
				entity.getPriceAmount() == null ? 0 : entity.getPriceAmount(),
				entity.getCurrency(),
				entity.getMileageKm() == null ? 0 : entity.getMileageKm(),
				entity.getFuel(),
				entity.getTransmission(),
				entity.getLocation(),
				entity.getDescription(),
				images,
				new ListingView.SellerView(entity.getSellerId(), entity.getSellerName(), entity.getSellerKind()),
				entity.getPublishedAt() == null ? null : entity.getPublishedAt().toInstant()
		);
	}

	private static String blank(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String sellerName(ParsedListing listing) {
		if (listing.sellerName() != null && !listing.sellerName().isBlank()) return listing.sellerName();
		return "dealer".equals(listing.sellerKind()) ? "Дилер" : "Частник";
	}
}
