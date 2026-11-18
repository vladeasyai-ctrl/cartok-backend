package pl.cartok.ingest;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.cartok.config.CartokProperties;

/** Один дозапрос отдельного ingest: страница поиска otomoto пишется в ту же базу. */
@Component
public class IngestClient {

	private static final Logger log = LoggerFactory.getLogger(IngestClient.class);

	private final String url;
	private final String token;
	private final RestClient http;

	public IngestClient(CartokProperties properties) {
		String configured = properties.ingest().url();
		this.url = configured == null ? "" : configured.trim();
		String secret = properties.ingest().token();
		this.token = secret == null ? "" : secret;
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(Duration.ofSeconds(25));
		this.http = RestClient.builder().requestFactory(factory).build();
	}

	public void demand(String make, String model) {
		if (url.isBlank() || make == null || make.isBlank()) return;
		Map<String, String> body = new LinkedHashMap<>();
		body.put("source", "demand");
		body.put("make", make);
		if (model != null && !model.isBlank()) body.put("model", model);
		try {
			http.post()
					.uri(url)
					.header("X-Ingest-Token", token)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.toBodilessEntity();
		} catch (RuntimeException error) {
			log.warn("Дозапрос ingest «{} {}» не прошёл: {}", make, model == null ? "" : model, error.toString());
		}
	}
}
