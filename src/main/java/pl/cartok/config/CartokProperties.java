package pl.cartok.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cartok")
public record CartokProperties(Otomoto otomoto, Ingest ingest) {

	public record Otomoto(
			String baseUrl,
			List<String> makes,
			String userAgent
	) {
	}

	public record Ingest(
			boolean enabled,
			Duration minDelay,
			Duration maxDelay,
			String token
	) {
	}
}
