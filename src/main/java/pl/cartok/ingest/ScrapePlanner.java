package pl.cartok.ingest;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.cartok.config.CartokProperties;

/**
 * Решает, что скачать с otomoto в следующий прогон.
 *
 * Интерес — сумма преференсов из preference_demand: уровень 1/2/3 даёт 6/3/1 очка.
 * Лайки и просмотры сюда не входят. Запас — сколько активных объявлений этой марки/модели
 * в среднем не видел активный пользователь. Качаем, где дефицит = интерес / (запас + 1) больше всего.
 * Каждый exploreEvery-й прогон — разведка: давно не обновлённая марка из конфига.
 */
@Component
public class ScrapePlanner {

	private static final String DEMAND = """
			select make, model, weight from preference_demand
			""";

	private static final String ACTIVE_LISTINGS = """
			select lower(make) as make, lower(model) as model, count(*) as amount
			from listing where status = 'active' and make <> ''
			group by 1, 2
			""";

	private static final String ACTIVE_USERS_FILTER =
			"select user_id from listing_interaction where last_event_at > now() - interval '7 days'";

	private static final String SEEN_BY_ACTIVE_USERS = """
			select lower(l.make) as make, lower(l.model) as model, count(*) as amount
			from listing_interaction i
			join listing l on l.id = i.listing_id
			where l.status = 'active' and i.seen_at is not null
				and i.user_id in (%s)
			group by 1, 2
			""".formatted(ACTIVE_USERS_FILTER);

	private static final String ACTIVE_USERS = "select count(distinct user_id) from (" + ACTIVE_USERS_FILTER + ") active";

	private static final String SLUGS = """
			select lower(make) as make, lower(model) as model, max(make_slug) as make_slug, max(model_slug) as model_slug
			from listing where make_slug <> ''
			group by 1, 2
			""";

	private final JdbcTemplate jdbc;
	private final MakeSelector makes;
	private final ScrapeQueryRepository states;
	private final int exploreEvery;
	private final AtomicInteger runs = new AtomicInteger();

	public ScrapePlanner(JdbcTemplate jdbc, MakeSelector makes, ScrapeQueryRepository states, CartokProperties properties) {
		this.jdbc = jdbc;
		this.makes = makes;
		this.states = states;
		this.exploreEvery = properties.ingest().exploreEvery();
	}

	public Plan next() {
		if (exploreEvery > 0 && runs.incrementAndGet() % exploreEvery == 0) {
			return explore("разведка: каждый " + exploreEvery + "-й прогон");
		}
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		for (Candidate candidate : rank()) {
			boolean resting = states.findById(candidate.query().key()).map(state -> state.resting(now)).orElse(false);
			if (!resting) return new Plan(candidate.query(), candidate.describe(), false);
		}
		return explore("разведка: интереса пока нет или все нужные поиски отдыхают");
	}

	private Plan explore(String reason) {
		return new Plan(ScrapeQuery.make(makes.next()), reason, true);
	}

	/** Все марки и модели с интересом, самый большой дефицит первым. */
	@Transactional(readOnly = true)
	public List<Candidate> rank() {
		Interest interest = loadInterest();
		Supply supply = loadSupply();
		Slugs slugs = loadSlugs();
		List<Candidate> candidates = new ArrayList<>();
		interest.points.forEach((target, points) -> {
			if (points <= 0) return;
			double stock = supply.of(target);
			candidates.add(new Candidate(target.label(), points, stock, points / (stock + 1), queryFor(target, slugs)));
		});
		candidates.sort(Comparator.comparingDouble(Candidate::deficit).reversed());
		return candidates;
	}

	private Interest loadInterest() {
		Interest interest = new Interest();
		jdbc.query(DEMAND, (ResultSet row) -> {
			String model = row.getString("model");
			interest.add(new Target(row.getString("make"), model == null ? "" : model), row.getDouble("weight"));
		});
		return interest;
	}

	private Supply loadSupply() {
		Supply supply = new Supply();
		jdbc.query(ACTIVE_LISTINGS, (ResultSet row) -> supply.total(row.getString("make"), row.getString("model"), row.getLong("amount")));
		jdbc.query(SEEN_BY_ACTIVE_USERS, (ResultSet row) -> supply.seen(row.getString("make"), row.getString("model"), row.getLong("amount")));
		Long users = jdbc.queryForObject(ACTIVE_USERS, Long.class);
		supply.activeUsers = users == null ? 0 : users;
		return supply;
	}

	private Slugs loadSlugs() {
		Slugs slugs = new Slugs();
		jdbc.query(SLUGS, (ResultSet row) -> {
			String make = row.getString("make");
			slugs.makes.putIfAbsent(make, row.getString("make_slug"));
			String modelSlug = row.getString("model_slug");
			if (modelSlug != null && !modelSlug.isBlank()) slugs.models.put(new Target(make, row.getString("model")), modelSlug);
		});
		return slugs;
	}

	private static ScrapeQuery queryFor(Target target, Slugs slugs) {
		String makeSlug = slugs.makes.getOrDefault(target.make(), ScrapeQuery.slugify(target.make()));
		String modelSlug = target.model().isBlank() ? "" : slugs.models.getOrDefault(target, ScrapeQuery.slugify(target.model()));
		return new ScrapeQuery(makeSlug, modelSlug, null, null, null, null, null);
	}

	/** make/model — подписи в нижнем регистре, как в listing; model "" — вся марка. */
	record Target(String make, String model) {

		String label() {
			return model.isBlank() ? make + " (вся марка)" : make + " " + model;
		}
	}

	public record Candidate(String target, double interest, double supply, double deficit, ScrapeQuery query) {

		String describe() {
			return String.format(Locale.ROOT, "%s: интерес %.1f, запас %.1f, дефицит %.2f", target, interest, supply, deficit);
		}
	}

	public record Plan(ScrapeQuery query, String reason, boolean exploration) {
	}

	private static final class Interest {
		final Map<Target, Double> points = new HashMap<>();

		void add(Target target, double value) {
			points.merge(target, value, Double::sum);
		}
	}

	private static final class Supply {
		final Map<Target, Long> totalByModel = new HashMap<>();
		final Map<String, Long> totalByMake = new HashMap<>();
		final Map<Target, Long> seenByModel = new HashMap<>();
		final Map<String, Long> seenByMake = new HashMap<>();
		long activeUsers;

		void total(String make, String model, long amount) {
			totalByModel.merge(new Target(make, model), amount, Long::sum);
			totalByMake.merge(make, amount, Long::sum);
		}

		void seen(String make, String model, long amount) {
			seenByModel.merge(new Target(make, model), amount, Long::sum);
			seenByMake.merge(make, amount, Long::sum);
		}

		double of(Target target) {
			long total = target.model().isBlank()
					? totalByMake.getOrDefault(target.make(), 0L)
					: totalByModel.getOrDefault(target, 0L);
			if (activeUsers == 0) return total;
			long seen = target.model().isBlank()
					? seenByMake.getOrDefault(target.make(), 0L)
					: seenByModel.getOrDefault(target, 0L);
			return Math.max(0, total - (double) seen / activeUsers);
		}
	}

	private static final class Slugs {
		final Map<String, String> makes = new HashMap<>();
		final Map<Target, String> models = new HashMap<>();
	}
}
