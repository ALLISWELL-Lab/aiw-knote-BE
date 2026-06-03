package com.aiw.backend.infra.config.security;

import com.aiw.backend.infra.auth.jwt.JwtAuthenticationEntryPoint;
import com.aiw.backend.infra.auth.jwt.filter.JwtAuthenticationFilter;
import com.aiw.backend.infra.auth.jwt.filter.JwtExceptionFilter;
import com.aiw.backend.infra.auth.oauth2.CustomOAuth2UserService;
import com.aiw.backend.infra.auth.oauth2.OAuth2SuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtExceptionFilter jwtExceptionFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2SuccessHandler oAuth2SuccessHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            (requests) -> requests
                .requestMatchers("/favicon.ico", "/img/**", "/js/**","/css/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").permitAll()
                .requestMatchers("/", "/error", "/auth/login", "/auth/signup").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/members/**").permitAll()
                    .requestMatchers("/api/v1/projects/**").permitAll()
                    .requestMatchers("/api/v1/meetings/**").permitAll()
                    .requestMatchers("/api/v1/actionItems/**").permitAll()
                    .requestMatchers("/api/v1/mypage/**").permitAll()
                    .requestMatchers("/api/v1/comments/**").permitAll()
                    .requestMatchers("/api/v1/personalMemos/**").permitAll()
                    .requestMatchers("/api/v1/announcements/**").permitAll()
                    .requestMatchers("/api/v1/notifications/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
        )

        // 구글 OAuth2 로그인 설정 활성화
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
            .successHandler(oAuth2SuccessHandler)
        )
        // 에러 핸들링 브릿지 등록
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
        )
        // jwtAuthenticationEntryPoint 는 oauth 인증을 사용할 경우 제거
        .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public WebSecurityCustomizer webSecurityCustomizer() {
    return (web) -> web.ignoring()
        .requestMatchers(PathRequest.toStaticResources().atCommonLocations());
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // 1. 프론트엔드 포트 명시
    configuration.setAllowedOrigins(List.of("http://localhost:5173"));
    // 2. 사용할 HTTP 메서드 허용
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    // 3. 모든 헤더 요청 허용
    configuration.setAllowedHeaders(List.of("*"));
    // 4. 쿠키와 JWT 토큰 세션을 공유할 수 있도록 설정
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

