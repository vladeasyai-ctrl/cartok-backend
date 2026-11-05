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
import pl.cartok.config.CartokProperties;

@Configuration
@EnableScheduling
public class IngestScheduler implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

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
			return last == null ? Instant.now() : last.plus(randomDelay());
		});
	}

	private void run() {
		try {
			IngestService.Report report = ingest.ingestNext();
			log.info("Плановый сбор Otomoto {}: сохранено {}", report.make(), report.saved());
		} catch (RuntimeException error) {
			log.warn("Плановый сбор Otomoto не завершился: {}", error.toString());
		}
	}

	private Duration randomDelay() {
		long min = settings.minDelay().toMillis();
		long max = Math.max(settings.maxDelay().toMillis(), min);
		return Duration.ofMillis(ThreadLocalRandom.current().nextLong(min, max + 1));
	}
}
