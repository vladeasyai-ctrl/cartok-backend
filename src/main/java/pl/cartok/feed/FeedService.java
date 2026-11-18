package pl.cartok.feed;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import pl.cartok.ingest.IngestClient;
import pl.cartok.ingest.ScrapeQuery;
import pl.cartok.ingest.ScrapeQueryRepository;
import pl.cartok.ingest.ScrapeQueryState;
import pl.cartok.listing.ListingService;
import pl.cartok.preference.PreferenceService;
import pl.cartok.preference.Preferences;

@Service
public class FeedService {

	/** Сколько разных нехваток закрыть за одно чтение, чтобы новая пачка марок попала в ту же страницу. */
	private static final int DEMANDS_PER_READ = 3;

	private final ListingService listings;
	private final PreferenceService preferences;
	private final IngestClient ingest;
	private final ScrapeQueryRepository scrapes;

	public FeedService(
			ListingService listings,
			PreferenceService preferences,
			IngestClient ingest,
			ScrapeQueryRepository scrapes
	) {
		this.listings = listings;
		this.preferences = preferences;
		this.ingest = ingest;
		this.scrapes = scrapes;
	}

	public List<ListingView> anonymous(int limit, List<String> seen) {
		return listings.feed(limit, seen);
	}

	/**
	 * Сначала то, что уже в базе. Если маркам не хватает доли — до трёх дозапросов самых давних,
	 * затем чтение ещё раз. Один дозапрос — одна марка и модель.
	 */
	public List<ListingView> personal(String userId, int limit, List<String> seen) {
		Preferences saved = preferences.load(userId);
		if (saved.hasChoices()) {
			Set<String> asked = new HashSet<>();
			for (int attempt = 0; attempt < DEMANDS_PER_READ; attempt++) {
				Preferences.Choice target = oldest(listings.shortfall(limit, seen, saved, userId));
				if (target == null) break;
				String model = target.model() == null ? "" : target.model();
				if (!asked.add(target.make().toLowerCase(Locale.ROOT) + "\n" + model.toLowerCase(Locale.ROOT))) break;
				ingest.demand(target.make(), target.model());
			}
		}
		return listings.feed(limit, seen, saved, userId);
	}

	private Preferences.Choice oldest(List<Preferences.Choice> choices) {
		if (choices.isEmpty()) return null;
		return choices.stream()
				.min(Comparator.comparing(this::lastRun, Comparator.nullsFirst(Comparator.naturalOrder())))
				.orElse(null);
	}

	private OffsetDateTime lastRun(Preferences.Choice choice) {
		String make = ScrapeQuery.slugify(choice.make());
		String model = choice.model() == null || choice.model().isBlank() ? "" : ScrapeQuery.slugify(choice.model());
		String key = model.isEmpty() ? make : make + "/" + model;
		return scrapes.findById(key).map(ScrapeQueryState::getLastRunAt).orElse(null);
	}
}
