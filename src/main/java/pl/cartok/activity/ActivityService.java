package pl.cartok.activity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Копит в listing_interaction, что пользователь делал с объявлением.
 * Просмотренным объявление считается после SEEN_AFTER_MS суммарного показа, открытия подробностей или лайка.
 */
@Service
public class ActivityService {

	public static final int MAX_EVENTS = 50;
	static final long SEEN_AFTER_MS = 800;
	/** Короче — просто пролистнул мимо, такой показ не пишем. */
	private static final long MIN_VIEW_MS = 300;
	private static final long MAX_VIEW_MS = 10 * 60 * 1000;
	private static final Pattern LISTING_ID = Pattern.compile("^[A-Za-z0-9_-]{1,80}$");

	private static final String VIEW = """
			insert into listing_interaction (user_id, listing_id, first_event_at, last_event_at, seen_at, views, dwell_ms)
			values (:userId, :listingId, :now, :now, case when :dwell >= :seenAfter then :now end, 1, :dwell)
			on conflict (user_id, listing_id) do update set
				last_event_at = excluded.last_event_at,
				views = listing_interaction.views + 1,
				dwell_ms = listing_interaction.dwell_ms + excluded.dwell_ms,
				seen_at = coalesce(listing_interaction.seen_at,
					case when listing_interaction.dwell_ms + excluded.dwell_ms >= :seenAfter then excluded.last_event_at end)
			""";

	private static final String OPEN = """
			insert into listing_interaction (user_id, listing_id, first_event_at, last_event_at, seen_at, opens)
			values (:userId, :listingId, :now, :now, :now, 1)
			on conflict (user_id, listing_id) do update set
				last_event_at = excluded.last_event_at,
				opens = listing_interaction.opens + 1,
				seen_at = coalesce(listing_interaction.seen_at, excluded.seen_at)
			""";

	private static final String LIKE = """
			insert into listing_interaction (user_id, listing_id, first_event_at, last_event_at, seen_at, liked_at)
			values (:userId, :listingId, :now, :now, :now, :now)
			on conflict (user_id, listing_id) do update set
				last_event_at = excluded.last_event_at,
				seen_at = coalesce(listing_interaction.seen_at, excluded.seen_at),
				liked_at = coalesce(listing_interaction.liked_at, excluded.liked_at)
			""";

	private static final String UNLIKE = """
			update listing_interaction set liked_at = null, last_event_at = :now
			where user_id = :userId and listing_id = :listingId
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public ActivityService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Возвращает, сколько событий записано. Чужие для бекенда объявления (созданные во фронтенде) пропускаются. */
	@Transactional
	public int record(String userId, List<Event> events) {
		List<Event> valid = events.stream()
				.filter(event -> event != null && event.listingId() != null && LISTING_ID.matcher(event.listingId()).matches())
				.filter(event -> event.kind() != null)
				.limit(MAX_EVENTS)
				.toList();
		if (valid.isEmpty()) return 0;
		Set<String> known = knownListings(valid.stream().map(Event::listingId).toList());
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		int written = 0;
		for (Event event : valid) {
			if (!known.contains(event.listingId())) continue;
			MapSqlParameterSource params = new MapSqlParameterSource()
					.addValue("userId", userId)
					.addValue("listingId", event.listingId())
					.addValue("now", now);
			String sql = switch (event.kind()) {
				case VIEW -> {
					long dwell = Math.min(event.dwellMs() == null ? 0 : event.dwellMs(), MAX_VIEW_MS);
					if (dwell < MIN_VIEW_MS) yield null;
					params.addValue("dwell", dwell).addValue("seenAfter", SEEN_AFTER_MS);
					yield VIEW;
				}
				case OPEN -> OPEN;
				case LIKE -> LIKE;
				case UNLIKE -> UNLIKE;
			};
			if (sql == null) continue;
			jdbc.update(sql, params);
			written++;
		}
		return written;
	}

	private Set<String> knownListings(List<String> ids) {
		return new HashSet<>(jdbc.queryForList(
				"select id from listing where id in (:ids)", Map.of("ids", Set.copyOf(ids)), String.class));
	}

	public enum Kind {
		@JsonProperty("view") VIEW,
		@JsonProperty("open") OPEN,
		@JsonProperty("like") LIKE,
		@JsonProperty("unlike") UNLIKE
	}

	public record Event(String listingId, Kind kind, Long dwellMs) {
	}
}
