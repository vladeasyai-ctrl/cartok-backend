package pl.cartok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.cartok.config.CartokProperties;

@SpringBootApplication
@EnableConfigurationProperties(CartokProperties.class)
public class CartokApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CartokApiApplication.class, args);
	}
}
