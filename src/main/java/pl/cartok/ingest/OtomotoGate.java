package pl.cartok.ingest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.cartok.config.CartokProperties;

/**
 * Кран к otomoto: не больше одного прогона за interval, плановые и ручные считаются вместе.
 * Помнит только в памяти — рассчитан на один экземпляр бекенда.
 */
@Component
public class OtomotoGate {

	private final Duration interval;
	private final Clock clock = Clock.systemUTC();
	private Instant lastStart;

	public OtomotoGate(CartokProperties properties) {
		this.interval = properties.ingest().interval();
	}

	public synchronized void acquire() {
		Instant now = clock.instant();
		if (lastStart != null) {
			Duration wait = Duration.between(now, lastStart.plus(interval));
			if (!wait.isNegative() && !wait.isZero()) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
						"Следующий сбор с otomoto не раньше чем через " + Math.max(1, wait.toSeconds()) + " с");
			}
		}
		lastStart = now;
	}

	public Duration interval() {
		return interval;
	}
}
