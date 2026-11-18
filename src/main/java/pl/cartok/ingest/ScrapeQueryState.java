package pl.cartok.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Что мы помним о поиске между прогонами: какую страницу брать дальше и не «выжат» ли он. */
@Entity
@Table(name = "scrape_query")
public class ScrapeQueryState {

	/** Глубже не листаем: дальше старые объявления, а свежие появятся на первой странице. */
	static final int MAX_PAGES = 5;
	/** После такого перерыва снова начинаем с первой страницы: там уже новые объявления. */
	private static final Duration RESTART_AFTER = Duration.ofHours(24);
	private static final Duration REST_EXHAUSTED = Duration.ofHours(6);
	private static final Duration REST_EMPTY = Duration.ofHours(24);
	private static final int MAX_FAILURE_REST_HOURS = 6;

	@Id
	@Column(name = "query_key")
	private String queryKey;

	@Column(name = "next_page", nullable = false)
	private int nextPage = 1;

	@Column(name = "last_run_at")
	private OffsetDateTime lastRunAt;

	@Column(name = "last_saved", nullable = false)
	private int lastSaved;

	@Column(name = "last_total", nullable = false)
	private int lastTotal;

	@Column(nullable = false)
	private int failures;

	@Column(name = "resting_until")
	private OffsetDateTime restingUntil;

	protected ScrapeQueryState() {
	}

	ScrapeQueryState(String queryKey) {
		this.queryKey = queryKey;
	}

	boolean resting(OffsetDateTime now) {
		return restingUntil != null && restingUntil.isAfter(now);
	}

	int pageFor(OffsetDateTime now) {
		if (lastRunAt == null || lastRunAt.plus(RESTART_AFTER).isBefore(now)) return 1;
		return nextPage;
	}

	/**
	 * Страница листается дальше, только когда на ней не осталось новых объявлений, которые мы не успели скачать.
	 * Пустая выдача или конец глубины — поиск отдыхает и потом начинает сначала.
	 */
	void succeeded(OffsetDateTime now, int page, int cards, int total, int saved, int leftNew) {
		lastRunAt = now;
		lastSaved = saved;
		lastTotal = total;
		failures = 0;
		restingUntil = null;
		if (cards == 0) {
			nextPage = 1;
			restingUntil = now.plus(page == 1 ? REST_EMPTY : REST_EXHAUSTED);
		} else if (leftNew > 0) {
			nextPage = page;
		} else if (page >= MAX_PAGES) {
			nextPage = 1;
			restingUntil = now.plus(REST_EXHAUSTED);
		} else {
			nextPage = page + 1;
		}
	}

	void failed(OffsetDateTime now) {
		lastRunAt = now;
		failures++;
		restingUntil = now.plusHours(Math.min(failures, MAX_FAILURE_REST_HOURS));
	}

	public String getQueryKey() {
		return queryKey;
	}

	public int getNextPage() {
		return nextPage;
	}

	public OffsetDateTime getLastRunAt() {
		return lastRunAt;
	}

	public int getLastSaved() {
		return lastSaved;
	}

	public int getLastTotal() {
		return lastTotal;
	}

	public int getFailures() {
		return failures;
	}

	public OffsetDateTime getRestingUntil() {
		return restingUntil;
	}
}
