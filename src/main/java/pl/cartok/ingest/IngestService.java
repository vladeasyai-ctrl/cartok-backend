package pl.cartok.ingest;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.cartok.config.CartokProperties;
import pl.cartok.listing.ListingService;

@Service
public class IngestService {

	private static final Logger log = LoggerFactory.getLogger(IngestService.class);
	private static final long OFFER_PAUSE_MS = 250;

	private final OtomotoApi otomoto;
	private final OtomotoParser parser;
	private final MakeSelector makes;
	private final ListingService listings;
	private final URI baseUrl;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public IngestService(
			OtomotoApi otomoto,
			OtomotoParser parser,
			MakeSelector makes,
			ListingService listings,
			CartokProperties properties
	) {
		this.otomoto = otomoto;
		this.parser = parser;
		this.makes = makes;
		this.listings = listings;
		this.baseUrl = URI.create(properties.otomoto().baseUrl());
	}

	public Report ingestNext() {
		return exclusively(() -> ingestPage(makes.next()));
	}

	public Report ingest(String make) {
		return exclusively(() -> ingestPage(make.trim().toLowerCase(Locale.ROOT)));
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

	private Report ingestPage(String make) {
		makes.attempted(make, now());
		ParsedListing.SearchPage page = parser.parseSearch(otomoto.search(make, 1, OtomotoApi.NEWEST_FIRST));
		int saved = 0;
		for (ParsedListing card : page.listings()) {
			listings.save(withDetails(card.fillMake(make)), make, now());
			saved++;
			pause();
		}
		log.info("Otomoto {}: сохранено {} из {}", make, saved, page.totalCount());
		return new Report(make, saved);
	}

	private ParsedListing withDetails(ParsedListing card) {
		if (card.sourceUrl().isBlank()) return card;
		URI url = baseUrl.resolve(card.sourceUrl());
		try {
			ParsedListing offer = parser.parseOffer(otomoto.offer(url));
			return offer == null ? card : card.withDetails(offer);
		} catch (RuntimeException error) {
			log.warn("Оффер {} не прочитан: {}", url, error.toString());
			return card;
		}
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	private static void pause() {
		try {
			Thread.sleep(OFFER_PAUSE_MS);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}

	public record Report(String make, int saved) {
	}
}
