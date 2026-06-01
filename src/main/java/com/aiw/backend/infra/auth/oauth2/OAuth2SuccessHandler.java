package com.aiw.backend.infra.auth.oauth2;

import com.aiw.backend.app.model.auth.AuthService;
import com.aiw.backend.app.model.auth.dto.TokenDto;
import com.aiw.backend.app.model.auth.token.RefreshTokenService;
import com.aiw.backend.app.model.auth.token.UserBlackListRepository;
import com.aiw.backend.app.model.auth.token.entity.RefreshToken;
import com.aiw.backend.infra.auth.jwt.JwtTokenProvider;
import com.aiw.backend.infra.auth.jwt.TokenCookieFactory;
import com.aiw.backend.infra.auth.jwt.dto.AccessTokenDto;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final RefreshTokenService refreshTokenService;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserBlackListRepository userBlackListRepository;
  private final CookieAuthorizationRequestRepository cookieAuthorizationRequestRepository;

  @Value("${front-server.domain-A}")
  private String frontServerDomainA;

  @Value("${url.backend}")
  private String backendServer;

  @Value("${front-server.redirect-url}")
  private String DEFAULT_REDIRECT_URL;

  // 도메인 안전하게 초기화
  @PostConstruct
  public void init() {
    this.ALLOWED_DOMAINS = Arrays.asList(
        frontServerDomainA,
        backendServer,
        "https://localhost:5173"
    );
  }

  // 허용 도메인
  private List<String> ALLOWED_DOMAINS;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {

    OAuth2User user = (OAuth2User) authentication.getPrincipal();
    String email = (String) user.getAttributes().get("email");
    String roles = "ROLE_USER"; // 구글 로그인은 기본 유저 권한으로 설정

    userBlackListRepository.deleteById(email);

    AccessTokenDto accessToken = jwtTokenProvider.generateAccessToken(email, roles);
    RefreshToken refreshToken = refreshTokenService.saveWithAtId(accessToken.getJti());

    ResponseCookie accessTokenCookie = TokenCookieFactory.create(
        "ACCESS_TOKEN",
        accessToken.getToken(),
        jwtTokenProvider.getAccessTokenExpiration()
    );

    ResponseCookie refreshTokenCookie = TokenCookieFactory.create(
        "REFRESH_TOKEN",
        refreshToken.getToken(),
        jwtTokenProvider.getRefreshTokenExpiration()
    );

    response.addHeader("Set-Cookie", accessTokenCookie.toString());
    response.addHeader("Set-Cookie", refreshTokenCookie.toString());

    // 5. 로그인 완료 후 프런트엔드로 리다이렉트
    String targetUrl = "http://localhost:5173";
    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }

  protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {

    // 'redirect_uri' 쿠키에서 uri를 가져오기
    // redirect uri 지정하지 않을 경우. 추후 실제 배포 도메인으로 변경해야 함.
    String redirectUri = CookieUtils.getCookie(request, CookieAuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME)
        .map(Cookie::getValue)
        .orElse(DEFAULT_REDIRECT_URL); // 쿠키에 uri 없으면 default 로 이동

    // 해당 uri가 허용된 도메인인지 검증
    if (StringUtils.hasText(redirectUri) && isAllowedUrl(redirectUri)) {
      log.info("클라이언트 지정 redirect url :{}", redirectUri);
      return redirectUri;
    } else {
      log.info("허용되지 않거나 유효하지 않은 redirect uri이 요청되었습니다. 기본 url로 리다이렉트 합니다.");
      return DEFAULT_REDIRECT_URL;
    }
  }

  // redirect uri 검증 메서드
  private boolean isAllowedUrl(String url) {
    try {
      // 입력받은 url 문자열을 URI 객체로 파싱
      URI uri = new URI(url);
      String host = uri.getHost(); // 로컬 테스트를 위해 host의 포트번호가 달라도 허용합니다.
      String scheme = uri.getScheme();

      if (!("http".equalsIgnoreCase(scheme)|| "https".equalsIgnoreCase(scheme))) {
        return false;
      }

      if (host == null) {
        return false;
      }

      return ALLOWED_DOMAINS.stream()
          .anyMatch(allowedDomain -> {
            try {
              URI allowedUri = new URI(allowedDomain);
              return allowedUri.getScheme().equalsIgnoreCase(scheme) &&allowedUri.getHost().equalsIgnoreCase(host);
            } catch (URISyntaxException e) {
              return false;
            }
          });
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
