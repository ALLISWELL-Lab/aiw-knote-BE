package com.aiw.backend.infra.auth.oauth2;

import com.aiw.backend.app.model.auth.token.RefreshTokenService;
import com.aiw.backend.app.model.auth.token.UserBlackListRepository;
import com.aiw.backend.app.model.auth.token.entity.RefreshToken;
import com.aiw.backend.app.model.member.domain.Member;
import com.aiw.backend.app.model.member.repository.MemberRepository;
import com.aiw.backend.app.model.team_member.repository.TeamMemberRepository;
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
import java.util.Optional;
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
  private final MemberRepository memberRepository;
  private final TeamMemberRepository teamMemberRepository;

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

    // 기존 유저 or 새로운 유저 판단
    boolean isNewUser = checkIsNewUser(email);

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

    String frontBaseUrl = "http://localhost:5173"; // 프로드 환경이면 환경변수 처리
    String targetUrl;

    if (isNewUser) {
      // 최초 로그인 사용자 -> 온보딩 페이지로 유도
      targetUrl = frontBaseUrl + "/team-onboarding";
      log.info("신규 사용자 로그인 - 온보딩 페이지로 리다이렉트: {}", email);
    } else {
      // 기존 가입 사용자 -> 대시보드(또는 메인 화면)로 유도
      targetUrl = frontBaseUrl + "/dashboard";
      log.info("기존 사용자 로그인 - 대시보드로 리다이렉트: {}", email);
    }

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }

  // 신규 유저 판단을 위한 가이드 메서드
  private boolean checkIsNewUser(String email) {
    // 먼저 이메일로 유저가 등록되어 있는지 확인
    Optional<Member> memberOpt = memberRepository.findByEmail(email);

    if (memberOpt.isEmpty()) {
      // 아예 회원가입도 안 된 진짜 신규 유저라면 온보딩 대상
      return true;
    }

    // 회원은 존재하지만, 유저-팀 매핑 테이블에 이 유저의 기록이 없다면 팀이 없는 유저(true)
    Long memberId = memberOpt.get().getId();
    boolean hasActiveTeam = teamMemberRepository.existsByMemberIdAndActivatedTrue(memberId);

    return !hasActiveTeam; // 팀이 없으면(!true -> true) 온보딩 페이지로 리다이렉트
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
