package pl.cartok.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.server.ResponseStatusException;
import pl.cartok.config.CartokProperties;

@Configuration
@EnableScheduling
public class IngestScheduler implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);
	/** Небольшой разброс, чтобы обращения не шли ровно по часам. */
	private static final long MAX_JITTER_MS = 30_000;

	private final IngestService ingest;
	private final CartokProperties.Ingest settings;

	public IngestScheduler(IngestService ingest, CartokProperties properties) {
		this.ingest = ingest;
		this.settings = properties.ingest();
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		if (!settings.enabled()) return;
		registrar.addTriggerTask(this::run, context -> {
			Instant last = context.lastCompletion();
			return last == null ? Instant.now() : last.plus(nextDelay());
		});
	}

	private void run() {
		try {
			IngestService.Report report = ingest.ingestNext();
			log.info("Плановый сбор «{}»: сохранено {}", report.query(), report.saved());
		} catch (ResponseStatusException error) {
			log.info("Плановый сбор пропущен: {}", error.getReason());
		} catch (RuntimeException error) {
			log.warn("Плановый сбор otomoto не завершился: {}", error.toString());
		}
	}

	private Duration nextDelay() {
		return settings.interval().plusMillis(ThreadLocalRandom.current().nextLong(MAX_JITTER_MS + 1));
	}
}
