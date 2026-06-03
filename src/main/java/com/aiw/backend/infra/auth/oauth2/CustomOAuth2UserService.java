package com.aiw.backend.infra.auth.oauth2;

import com.aiw.backend.app.model.member.domain.Member;
import com.aiw.backend.app.model.member.repository.MemberRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private final MemberRepository memberRepository;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
    OAuth2User oAuth2User = delegate.loadUser(userRequest);

    // 구글 유저 정보 고유 키값 추출
    String userNameAttributeName = userRequest.getClientRegistration()
        .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

    Map<String, Object> attributes = oAuth2User.getAttributes();

    String email = (String) attributes.get("email");
    String name = (String) attributes.get("name");

    saveOrUpdate(email, name);

    return new DefaultOAuth2User(
        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
        attributes,
        userNameAttributeName
    );
  }

  // 구글 로그인 유저 자동 회원가입 및 정보 업데이트 메서드
  private void saveOrUpdate(String email, String name) {
    Optional<Member> existingMember = memberRepository.findByEmail(email);

    if (existingMember.isPresent()) {
      // 이미 가입된 유저라면 최신 구글 이름으로 업데이트 (선택 사항)
      Member member = existingMember.get();
      member.setName(name);
      memberRepository.save(member);
    } else {
      // 최초 로그인한 유저라면 DB에 방을 파서 꽂아넣기
      Member newMember = new Member();
      newMember.setEmail(email);
      newMember.setName(name != null ? name : "구글유저");
      newMember.setActivated(true); // 활성화 상태 열기

      newMember.setProvider("google");

      // 만아야 하는 필수값이 더 있다면 (예: role 등) 여기에 set으로 추가해 주시면 됩니다.
      memberRepository.save(newMember);
      System.out.println("[구글 자동 회원가입 완료] DB에 신규 회원 적재 성공: " + email);
    }
  }
}
