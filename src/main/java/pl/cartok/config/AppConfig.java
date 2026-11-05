package pl.cartok.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pl.cartok.ingest.OtomotoApi;

@Configuration
public class AppConfig {

	@Bean
	OtomotoApi otomotoApi(CartokProperties properties) {
		HttpClient http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(20));
		RestClient restClient = RestClient.builder()
				.requestFactory(factory)
				.baseUrl(properties.otomoto().baseUrl())
				.defaultHeader(HttpHeaders.USER_AGENT, properties.otomoto().userAgent())
				.defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "pl")
				.build();
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
				.build()
				.createClient(OtomotoApi.class);
	}

	@Bean
	WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/api/**")
						.allowedOrigins("http://localhost:3000")
						.allowedMethods("GET", "POST", "OPTIONS")
						.allowedHeaders("*");
			}
		};
	}
}
