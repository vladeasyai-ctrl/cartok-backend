package pl.cartok.listing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cartok.activity.InteractionRepository;
import pl.cartok.feed.ListingView;
import pl.cartok.ingest.ParsedListing;
import pl.cartok.preference.Preferences;

@Service
public class ListingService {

	private static final Sort FEED_ORDER = Sort.by(
			Sort.Order.desc("publishedAt").with(Sort.NullHandling.NULLS_LAST),
			Sort.Order.desc("id")
	);

	/** Сколько давно виденных перебирать, чтобы добить ленту, когда непросмотренное кончилось. */
	private static final int SEEN_FALLBACK_SCAN = 200;

	private final ListingRepository listings;
	private final InteractionRepository interactions;

	public ListingService(ListingRepository listings, InteractionRepository interactions) {
		this.listings = listings;
		this.interactions = interactions;
	}

	@Transactional
	public void save(ParsedListing parsed, String searchSlug, OffsetDateTime seenAt, boolean detailed) {
		ListingEntity entity = listings.findBySourceAndExternalId("otomoto", parsed.externalId())
				.orElseGet(ListingEntity::new);
		if (entity.getId() != null && entity.isDetailed() && !detailed) {
			entity.setLastSeenAt(seenAt);
			entity.setSearchSlug(searchSlug);
			listings.save(entity);
			return;
		}
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
		entity.setMakeSlug(blank(parsed.makeSlug(), ""));
		entity.setModelSlug(blank(parsed.modelSlug(), ""));
		entity.setSourceUrl(parsed.sourceUrl());
		entity.setStatus(blank(parsed.status(), "active"));
		entity.setPublishedAt(parsed.publishedAt() == null ? null : parsed.publishedAt().atOffset(ZoneOffset.UTC));
		entity.setLastSeenAt(seenAt);
		entity.setDetailed(detailed);
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

	/** Какие из объявлений otomoto (по externalId) уже есть в базе. */
	@Transactional(readOnly = true)
	public Set<String> knownExternalIds(Collection<String> externalIds) {
		if (externalIds.isEmpty()) return Set.of();
		return Set.copyOf(listings.findExternalIds("otomoto", externalIds));
	}

	@Transactional(readOnly = true)
	public Set<String> undetailedExternalIds(Collection<String> externalIds) {
		if (externalIds.isEmpty()) return Set.of();
		return Set.copyOf(listings.findUndetailedExternalIds("otomoto", externalIds));
	}

	@Transactional(readOnly = true)
	public List<ThinListing> undetailed(String makeSlug, int limit) {
		if (limit <= 0) return List.of();
		return listings.findUndetailed(makeSlug, PageRequest.of(0, limit));
	}

	/** Объявление снова встретилось в выдаче: оно ещё живо, подробности заново не качаем. */
	@Transactional
	public void touch(Collection<String> externalIds, String searchSlug, OffsetDateTime seenAt) {
		if (!externalIds.isEmpty()) listings.touch("otomoto", externalIds, searchSlug, seenAt);
	}

	@Transactional(readOnly = true)
	public Map<String, OffsetDateTime> lastSeenBySlug() {
		return listings.lastSeenBySlug().stream()
				.collect(Collectors.toMap(SlugActivity::slug, SlugActivity::lastSeenAt));
	}

	@Transactional(readOnly = true)
	public List<ListingView> feed(int limit) {
		return listings.findByStatus("active", feedPage(limit)).stream().map(entity -> toView(entity, false)).toList();
	}

	@Transactional(readOnly = true)
	public List<ListingView> feed(int limit, List<String> seenIds) {
		return feed(limit, seenIds, Preferences.none(), null);
	}

	/**
	 * Марки, которым не хватает своей доли непросмотренных. Доля уровня делится поровну между его марками,
	 * поэтому новая марка добирается, даже если уровень уже забит другой. Пустой список — доли закрыты.
	 * Вызывающий сам решает, качать ли otomoto, и затем читает ленту ещё раз.
	 */
	@Transactional(readOnly = true)
	public List<Preferences.Choice> shortfall(int limit, List<String> seenIds, Preferences preferences, String userId) {
		if (!preferences.hasChoices()) return List.of();
		Specification<ListingEntity> unseen = unseen(userId);
		List<String> excluded = List.copyOf(seenIds);
		int[] share = share(limit);
		List<Preferences.Choice> needed = new ArrayList<>();
		int worstGap = 0;
		for (int index = 0; index < Preferences.PRIORITY_COUNT; index++) {
			List<Preferences.Choice> choices = preferences.priorities().get(index);
			int tierLimit = share[index];
			if (choices.isEmpty() || tierLimit == 0) continue;
			Map<String, List<Preferences.Choice>> groups = byMake(choices);
			int perMake = Math.max(1, (tierLimit + groups.size() - 1) / groups.size());
			for (List<Preferences.Choice> group : groups.values()) {
				int found = listings.findBy(
						unseen.and(ListingSpecs.anyOf(group)).and(ListingSpecs.notIn(excluded)),
						query -> query.sortBy(FEED_ORDER).limit(perMake).all()).size();
				int gap = perMake - found;
				if (gap <= 0) continue;
				if (gap > worstGap) {
					worstGap = gap;
					needed = new ArrayList<>(group);
				} else if (gap == worstGap) {
					needed.addAll(group);
				}
			}
		}
		return List.copyOf(needed);
	}

	/**
	 * С преференсами страница делится 60/30/10. Внутри уровня марки чередуются, уровни тоже перемежаются,
	 * чтобы одна марка не шла сплошным блоком. Нехватка переходит на следующий уровень,
	 * затем добивается любыми ещё непросмотренными из выбранных марок. Чужие марки не попадают.
	 * Уже виденное — только когда непросмотренное по всем уровням кончилось.
	 * Без преференсов — просто новые непросмотренные.
	 */
	@Transactional(readOnly = true)
	public List<ListingView> feed(int limit, List<String> seenIds, Preferences preferences, String userId) {
		if (!preferences.hasChoices()) return plain(limit, seenIds, userId);

		Specification<ListingEntity> unseen = unseen(userId);
		List<String> excluded = List.copyOf(seenIds);
		List<List<ListingEntity>> pools = new ArrayList<>();
		for (List<Preferences.Choice> choices : preferences.priorities()) {
			pools.add(mixByMake(loadTier(unseen, excluded, choices, limit)));
		}
		int[] want = share(limit);
		int[] have = pools.stream().mapToInt(List::size).toArray();
		for (int index = 0; index < want.length - 1; index++) {
			int deficit = Math.max(0, want[index] - have[index]);
			want[index] = Math.min(want[index], have[index]);
			for (int next = index + 1; next < want.length && deficit > 0; next++) {
				int room = have[next] - want[next];
				if (room <= 0) continue;
				int give = Math.min(room, deficit);
				want[next] += give;
				deficit -= give;
			}
		}
		for (int index = 0; index < want.length; index++) want[index] = Math.min(want[index], have[index]);
		int filled = 0;
		for (int value : want) filled += value;
		for (int index = 0; filled < limit && index < want.length; index++) {
			int room = have[index] - want[index];
			if (room <= 0) continue;
			int give = Math.min(room, limit - filled);
			want[index] += give;
			filled += give;
		}

		Set<String> used = new LinkedHashSet<>();
		List<ListingEntity> picked = new ArrayList<>(limit);
		int[] cursor = new int[pools.size()];
		int[] left = want.clone();
		int weight = 0;
		for (int value : want) weight += value;
		int[] credit = new int[want.length];
		while (picked.size() < limit) {
			int best = -1;
			for (int index = 0; index < left.length; index++) {
				if (left[index] <= 0) continue;
				if (best < 0 || credit[index] > credit[best]) best = index;
			}
			if (best < 0) break;
			ListingEntity entity = takeFresh(pools.get(best), cursor, best, used);
			if (entity == null) {
				left[best] = 0;
				continue;
			}
			picked.add(entity);
			left[best]--;
			for (int index = 0; index < credit.length; index++) credit[index] += want[index];
			credit[best] -= weight;
		}
		if (picked.size() < limit) {
			addSeen(
					ListingSpecs.active().and(ListingSpecs.anyOf(allChoices(preferences))),
					seenFallback(seenIds, userId, picked),
					limit,
					picked);
		}
		return views(picked, userId);
	}

	/** По каждой марке уровня отдельно, иначе самые свежие одной марки занимают весь пул. */
	private List<ListingEntity> loadTier(
			Specification<ListingEntity> unseen,
			List<String> excluded,
			List<Preferences.Choice> choices,
			int limit
	) {
		if (choices.isEmpty()) return List.of();
		List<ListingEntity> pool = new ArrayList<>();
		for (List<Preferences.Choice> group : byMake(choices).values()) {
			pool.addAll(listings.findBy(
					unseen.and(ListingSpecs.anyOf(group)).and(ListingSpecs.notIn(excluded)),
					query -> query.sortBy(FEED_ORDER).limit(limit).all()));
		}
		return pool;
	}

	private static Map<String, List<Preferences.Choice>> byMake(List<Preferences.Choice> choices) {
		Map<String, List<Preferences.Choice>> groups = new LinkedHashMap<>();
		for (Preferences.Choice choice : choices) {
			String key = choice.make().toLowerCase(Locale.ROOT);
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(choice);
		}
		return groups;
	}

	/** Очереди марок в порядке предпочтений, из каждой берётся по одной машине за проход. */
	private static List<ListingEntity> mixByMake(List<ListingEntity> pool) {
		if (pool.size() < 2) return pool;
		Map<String, ArrayDeque<ListingEntity>> groups = new LinkedHashMap<>();
		for (ListingEntity entity : pool) {
			String make = entity.getMake() == null ? "" : entity.getMake().toLowerCase(Locale.ROOT);
			groups.computeIfAbsent(make, ignored -> new ArrayDeque<>()).addLast(entity);
		}
		if (groups.size() < 2) return pool;
		List<ListingEntity> mixed = new ArrayList<>(pool.size());
		boolean pending = true;
		while (pending) {
			pending = false;
			for (ArrayDeque<ListingEntity> queue : groups.values()) {
				ListingEntity next = queue.pollFirst();
				if (next == null) continue;
				mixed.add(next);
				pending = true;
			}
		}
		return mixed;
	}

	private static ListingEntity takeFresh(List<ListingEntity> lane, int[] cursor, int index, Set<String> used) {
		while (cursor[index] < lane.size()) {
			ListingEntity entity = lane.get(cursor[index]++);
			if (used.add(entity.getId())) return entity;
		}
		return null;
	}

	private List<ListingView> plain(int limit, List<String> seenIds, String userId) {
		Specification<ListingEntity> base = ListingSpecs.active();
		Set<String> excluded = new LinkedHashSet<>(seenIds);
		List<ListingEntity> picked = new ArrayList<>(limit);
		take(unseen(userId), excluded, limit, picked);
		if (picked.size() < limit) addSeen(base, seenFallback(seenIds, userId, picked), limit, picked);
		return views(picked, userId);
	}

	private List<ListingView> views(List<ListingEntity> picked, String userId) {
		Set<String> liked = userId == null || picked.isEmpty()
				? Set.of()
				: Set.copyOf(interactions.likedAmong(userId, picked.stream().map(ListingEntity::getId).toList()));
		return picked.stream().map(entity -> toView(entity, liked.contains(entity.getId()))).toList();
	}

	private Specification<ListingEntity> unseen(String userId) {
		Specification<ListingEntity> base = ListingSpecs.active();
		return userId == null ? base : base.and(ListingSpecs.notSeenBy(userId));
	}

	/** Доли страницы: уровень 1 — 60%, уровень 2 — 30%, уровень 3 — остаток, около 10%. */
	private static int[] share(int limit) {
		int rare = limit / 10;
		int less = limit * 3 / 10;
		return new int[] {limit - less - rare, less, rare};
	}

	private static List<Preferences.Choice> allChoices(Preferences preferences) {
		return preferences.priorities().stream().flatMap(Collection::stream).toList();
	}

	private List<String> seenFallback(List<String> seenIds, String userId, List<ListingEntity> picked) {
		Set<String> ids = new LinkedHashSet<>();
		if (userId != null) ids.addAll(interactions.seenOldestFirst(userId, PageRequest.of(0, SEEN_FALLBACK_SCAN)));
		ids.addAll(seenIds);
		picked.forEach(entity -> ids.remove(entity.getId()));
		return List.copyOf(ids);
	}

	private void addSeen(Specification<ListingEntity> base, List<String> ids, int limit, List<ListingEntity> picked) {
		if (ids.isEmpty()) return;
		Map<String, ListingEntity> seen = listings.findAll(base.and(ListingSpecs.idIn(ids))).stream()
				.collect(Collectors.toMap(ListingEntity::getId, Function.identity()));
		for (String id : ids) {
			if (picked.size() >= limit) return;
			ListingEntity entity = seen.get(id);
			if (entity != null) picked.add(entity);
		}
	}

	private void take(Specification<ListingEntity> bucket, Set<String> excluded, int limit, List<ListingEntity> picked) {
		int missing = limit - picked.size();
		if (missing <= 0) return;
		List<ListingEntity> found = listings.findBy(
				bucket.and(ListingSpecs.notIn(List.copyOf(excluded))),
				query -> query.sortBy(FEED_ORDER).limit(missing).all());
		for (ListingEntity entity : found) {
			picked.add(entity);
			excluded.add(entity.getId());
		}
	}

	private static PageRequest feedPage(int limit) {
		return PageRequest.of(0, limit, FEED_ORDER);
	}

	@Transactional(readOnly = true)
	public ListingView findActive(String id) {
		return listings.findByIdAndStatus(id, "active").map(entity -> toView(entity, false)).orElse(null);
	}

	private static ListingView toView(ListingEntity entity, boolean liked) {
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
				entity.getPublishedAt() == null ? null : entity.getPublishedAt().toInstant(),
				liked
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
