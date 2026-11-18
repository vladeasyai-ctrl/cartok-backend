package pl.cartok.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ручки с данными пользователей вызывает только сервер Next, браузер сюда не ходит.
 * Без SERVICE_TOKEN они закрыты целиком.
 */
public class ServiceTokenInterceptor implements HandlerInterceptor {

	public static final String HEADER = "X-Service-Token";

	private final byte[] expected;

	public ServiceTokenInterceptor(String token) {
		this.expected = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
		if ("OPTIONS".equals(request.getMethod())) return true;
		String token = request.getHeader(HEADER);
		if (expected != null && token != null && MessageDigest.isEqual(expected, token.getBytes(StandardCharsets.UTF_8))) {
			return true;
		}
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"error\":\"Неверный сервисный токен\"}");
		return false;
	}
}
