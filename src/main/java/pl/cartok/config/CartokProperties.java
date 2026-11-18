package pl.cartok.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cartok")
public record CartokProperties(Otomoto otomoto, Ingest ingest, String serviceToken) {

	public record Otomoto(
			String baseUrl,
			List<String> makes,
			String userAgent
	) {
	}

	/**
	 * interval — минимум между началами двух прогонов (плановых и ручных вместе).
	 * Прогон: одна страница поиска + подробности не больше offersPerRun новых объявлений.
	 * exploreEvery — каждый N-й прогон берёт давно не обновлённую марку вместо самой востребованной; 0 — без разведки.
	 * url — полный адрес POST отдельного ingest. Пустой: лента только читает базу.
	 */
	public record Ingest(
			boolean enabled,
			Duration interval,
			int offersPerRun,
			Duration offerPauseMin,
			Duration offerPauseMax,
			int exploreEvery,
			String token,
			String url
	) {
	}
}
