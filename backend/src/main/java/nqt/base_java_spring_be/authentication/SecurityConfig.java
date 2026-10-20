package nqt.base_java_spring_be.authentication;

import nqt.base_java_spring_be.repository.UserRepository;
import nqt.base_java_spring_be.security.JwtAuthenticationFilter;
import nqt.base_java_spring_be.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${APP_VEO_REQUIRE_AUTH:false}")
    private boolean veoRequireAuth;

    public SecurityConfig(UserDetailsService userDetailsService,
                          JwtTokenProvider tokenProvider,
                          UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .authorizeHttpRequests(authorize -> {
                        // Preflight cho CORS
                        authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                        authorize.requestMatchers("/api/auth/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/swagger-ui.html",
                                "/webjars/**").permitAll();
                        // Local Studio UI can call Veo without login; require JWT before public deployment.
                        if (!veoRequireAuth) authorize.requestMatchers("/api/veo/**").permitAll();
                        // SockJS info probe phải mở để client lấy thông tin trước khi handshake
                        authorize.requestMatchers("/ws/info/**", "/ws/info").permitAll();
                        // Handshake & các transport SockJS/WebSocket thật sự yêu cầu JWT
                        authorize.requestMatchers("/ws/**").permitAll();
                        authorize.anyRequest().authenticated();
                })
                .userDetailsService(userDetailsService)
                .sessionManagement(sm -> sm.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, userRepository),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:6660","http://localhost:8686","http://127.0.0.1:3000","http://127.0.0.1:6660","http://127.0.0.1:8686")); // Thêm miền của Studio UI
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*")); // Cho phép tất cả các header
        configuration.setAllowCredentials(true); // Nếu bạn cần hỗ trợ cookie

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Cấu hình CORS cho tất cả các đường dẫn
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}





