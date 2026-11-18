package pl.cartok.ingest;

import java.time.Instant;
import java.util.List;

/** make/model — подписи для людей (BMW, Seria 3), makeSlug/modelSlug — как в адресе поиска otomoto (bmw, seria-3). */
public record ParsedListing(
		String externalId,
		String title,
		String make,
		String model,
		String makeSlug,
		String modelSlug,
		Integer year,
		Integer priceAmount,
		String currency,
		Integer mileageKm,
		String fuel,
		String transmission,
		String location,
		String description,
		String sellerId,
		String sellerName,
		String sellerKind,
		String sourceUrl,
		Instant publishedAt,
		String status,
		List<Photo> photos
) {

	public ParsedListing {
		photos = photos == null ? List.of() : List.copyOf(photos);
	}

	public String id() {
		return "oto_" + externalId;
	}

	public ParsedListing fillMake(String slug) {
		if (make != null && !make.isBlank()) return this;
		return new ParsedListing(
				externalId, title, slug, model, prefer(makeSlug, slug), modelSlug, year, priceAmount, currency, mileageKm,
				fuel, transmission, location, description, sellerId, sellerName, sellerKind, sourceUrl, publishedAt,
				status, photos
		);
	}

	public ParsedListing withDetails(ParsedListing details) {
		return new ParsedListing(
				externalId,
				prefer(details.title, title),
				prefer(details.make, make),
				prefer(details.model, model),
				prefer(details.makeSlug, makeSlug),
				prefer(details.modelSlug, modelSlug),
				prefer(details.year, year),
				details.priceAmount != null && details.priceAmount > 0 ? details.priceAmount : priceAmount,
				prefer(details.currency, currency),
				prefer(details.mileageKm, mileageKm),
				prefer(details.fuel, fuel),
				prefer(details.transmission, transmission),
				prefer(details.location, location),
				prefer(details.description, description),
				prefer(details.sellerId, sellerId),
				prefer(details.sellerName, sellerName),
				prefer(details.sellerKind, sellerKind),
				prefer(details.sourceUrl, sourceUrl),
				prefer(details.publishedAt, publishedAt),
				prefer(details.status, status),
				details.photos.isEmpty() ? photos : details.photos
		);
	}

	private static String prefer(String preferred, String fallback) {
		return preferred != null && !preferred.isBlank() ? preferred : fallback;
	}

	private static <T> T prefer(T preferred, T fallback) {
		return preferred != null ? preferred : fallback;
	}

	public record Photo(String fileId, String url) {
	}

	public record SearchPage(int totalCount, int offset, List<ParsedListing> listings) {
	}
}
