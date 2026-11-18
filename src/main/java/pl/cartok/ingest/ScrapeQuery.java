package pl.cartok.ingest;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Один поиск на otomoto: марка, необязательная модель и фильтры (null — без фильтра).
 * fuel — наше значение (petrol, diesel, hybrid, electric, lpg), в адрес переводится в otomoto-шное.
 */
public record ScrapeQuery(
		String makeSlug,
		String modelSlug,
		String fuel,
		Integer yearFrom,
		Integer yearTo,
		Integer mileageFrom,
		Integer mileageTo
) {

	static final String NEWEST_FIRST = "created_at_first:desc";

	public static ScrapeQuery make(String makeSlug) {
		return new ScrapeQuery(makeSlug, "", null, null, null, null, null);
	}

	public ScrapeQuery withoutFilters() {
		return new ScrapeQuery(makeSlug, modelSlug, null, null, null, null, null);
	}

	public boolean filtered() {
		return fuel != null || yearFrom != null || yearTo != null || mileageFrom != null || mileageTo != null;
	}

	/** Ключ для scrape_query: одинаковые поиски помнят общую страницу и отдых. */
	public String key() {
		List<String> parts = new ArrayList<>();
		parts.add(makeSlug + (modelSlug.isBlank() ? "" : "/" + modelSlug));
		if (fuel != null) parts.add("fuel=" + fuel);
		if (yearFrom != null || yearTo != null) parts.add("year=" + bound(yearFrom) + "-" + bound(yearTo));
		if (mileageFrom != null || mileageTo != null) parts.add("km=" + bound(mileageFrom) + "-" + bound(mileageTo));
		return String.join(" ", parts);
	}

	URI searchUri(String baseUrl, int page) {
		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("osobowe", makeSlug);
		if (!modelSlug.isBlank()) uri.pathSegment(modelSlug);
		uri.queryParam("search[order]", NEWEST_FIRST).queryParam("page", page);
		if (fuel != null) uri.queryParam("search[filter_enum_fuel_type]", otomotoFuel(fuel));
		if (yearFrom != null) uri.queryParam("search[filter_float_year:from]", yearFrom);
		if (yearTo != null) uri.queryParam("search[filter_float_year:to]", yearTo);
		if (mileageFrom != null) uri.queryParam("search[filter_float_mileage:from]", mileageFrom);
		if (mileageTo != null) uri.queryParam("search[filter_float_mileage:to]", mileageTo);
		return uri.encode().build().toUri();
	}

	/** Запасной вариант, когда адресного названия ещё нет в базе: «Seria 3» → «seria-3». */
	public static String slugify(String label) {
		String plain = Normalizer.normalize(label.toLowerCase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return plain.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String otomotoFuel(String fuel) {
		return "lpg".equals(fuel) ? "petrol-lpg" : fuel;
	}

	private static String bound(Integer value) {
		return value == null ? "" : String.valueOf(value);
	}
}
