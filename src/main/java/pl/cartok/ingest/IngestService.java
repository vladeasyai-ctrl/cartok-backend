package pl.cartok.ingest;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.cartok.config.CartokProperties;
import pl.cartok.listing.ListingService;
import pl.cartok.listing.ThinListing;

/**
 * Прогон: одна страница поиска otomoto и подробности не больше offersPerRun объявлений.
 * Уже подробные объявления только отмечаются живыми. Карточки без страницы оффера дочитываются.
 */
@Service
public class IngestService {

	private static final Logger log = LoggerFactory.getLogger(IngestService.class);

	private final OtomotoApi otomoto;
	private final OtomotoParser parser;
	private final ScrapePlanner planner;
	private final MakeSelector makes;
	private final OtomotoGate gate;
	private final ScrapeQueryRepository states;
	private final ListingService listings;
	private final String baseUrl;
	private final CartokProperties.Ingest settings;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public IngestService(
			OtomotoApi otomoto,
			OtomotoParser parser,
			ScrapePlanner planner,
			MakeSelector makes,
			OtomotoGate gate,
			ScrapeQueryRepository states,
			ListingService listings,
			CartokProperties properties
	) {
		this.otomoto = otomoto;
		this.parser = parser;
		this.planner = planner;
		this.makes = makes;
		this.gate = gate;
		this.states = states;
		this.listings = listings;
		this.baseUrl = properties.otomoto().baseUrl();
		this.settings = properties.ingest();
	}

	public Report ingestNext() {
		return exclusively(() -> run(planner.next()));
	}

	public Report ingest(String make) {
		ScrapeQuery query = ScrapeQuery.make(make.trim().toLowerCase(Locale.ROOT));
		return exclusively(() -> run(new ScrapePlanner.Plan(query, "ручной запуск", false)));
	}

	private Report exclusively(Supplier<Report> job) {
		if (!running.compareAndSet(false, true)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Сбор уже идёт");
		}
		try {
			return job.get();
		} finally {
			running.set(false);
		}
	}

	private Report run(ScrapePlanner.Plan plan) {
		gate.acquire();
		ScrapeQuery query = plan.query();
		OffsetDateTime now = now();
		ScrapeQueryState state = states.findById(query.key()).orElseGet(() -> new ScrapeQueryState(query.key()));
		int page = state.pageFor(now);
		log.info("Сбор otomoto «{}», страница {} — {}", query.key(), page, plan.reason());
		makes.attempted(query.makeSlug(), now);

		ParsedListing.SearchPage result;
		try {
			result = parser.parseSearch(otomoto.page(query.searchUri(baseUrl, page)));
		} catch (RuntimeException error) {
			state.failed(now);
			states.save(state);
			throw error;
		}

		List<ParsedListing> cards = result.listings();
		Set<String> known = listings.knownExternalIds(cards.stream().map(ParsedListing::externalId).toList());
		Set<String> thin = listings.undetailedExternalIds(known);
		Set<String> ready = new HashSet<>(known);
		ready.removeAll(thin);
		listings.touch(ready, query.makeSlug(), now);
		List<ParsedListing> queue = cards.stream().filter(card -> !ready.contains(card.externalId())).toList();
		int savedOnPage = 0;
		Set<String> attempted = new HashSet<>();
		for (ParsedListing card : queue) {
			if (savedOnPage >= settings.offersPerRun()) break;
			if (savedOnPage > 0) pause();
			attempted.add(card.externalId());
			ParsedListing filled = card.fillMake(query.makeSlug());
			ParsedListing offer = readOffer(filled);
			listings.save(offer == null ? filled : offer, query.makeSlug(), now(), offer != null);
			savedOnPage++;
		}
		int saved = savedOnPage;
		for (ThinListing row : listings.undetailed(query.makeSlug(), settings.offersPerRun())) {
			if (saved >= settings.offersPerRun()) break;
			if (!attempted.add(row.externalId())) continue;
			if (saved > 0) pause();
			ParsedListing offer = readOffer(stub(row.externalId(), row.sourceUrl(), query.makeSlug()));
			if (offer == null) continue;
			listings.save(offer, query.makeSlug(), now(), true);
			saved++;
		}
		state.succeeded(now, page, cards.size(), result.totalCount(), savedOnPage, queue.size() - savedOnPage);
		states.save(state);
		log.info("Otomoto «{}» стр. {}: сохранено {}, из них с этой страницы {}, ещё на странице ждёт {}, уже были подробные {}, всего в поиске {}",
				query.key(), page, saved, savedOnPage, queue.size() - savedOnPage, ready.size(), result.totalCount());
		return new Report(query.key(), page, saved, ready.size(), plan.reason());
	}

	/** null — страницу объявления прочитать не удалось, строку подробной не помечаем. */
	private ParsedListing readOffer(ParsedListing card) {
		if (card.sourceUrl() == null || card.sourceUrl().isBlank()) return null;
		URI url = URI.create(baseUrl).resolve(card.sourceUrl());
		try {
			ParsedListing offer = parser.parseOffer(otomoto.page(url));
			return offer == null ? null : card.withDetails(offer);
		} catch (RuntimeException error) {
			log.warn("Оффер {} не прочитан: {}", url, error.toString());
			return null;
		}
	}

	private static ParsedListing stub(String externalId, String sourceUrl, String makeSlug) {
		return new ParsedListing(
				externalId, "", "", "", makeSlug, "", null, null, null, null,
				"", "", "", "", "", "", "", sourceUrl, null, "active", List.of());
	}

	private void pause() {
		long min = settings.offerPauseMin().toMillis();
		long max = Math.max(settings.offerPauseMax().toMillis(), min);
		try {
			Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextLong(min, max + 1)));
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	public record Report(String query, int page, int saved, int known, String reason) {
	}
}
