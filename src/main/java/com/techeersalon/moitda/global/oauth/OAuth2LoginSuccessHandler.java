package com.techeersalon.moitda.global.oauth;

import com.techeersalon.moitda.domain.user.entity.Role;
import com.techeersalon.moitda.domain.user.repository.UserRepository;
import com.techeersalon.moitda.global.jwt.Service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        log.info("OAuth2 Login 성공!");
        try {
            CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

            Role role = oAuth2User.getRole();

            // User의 Role이 GUEST일 경우 처음 요청한 회원이므로 회원가입 페이지로 리다이렉트
            if (role == Role.GUEST) {
                String accessToken = jwtService.createAccessToken(oAuth2User.getEmail(), oAuth2User.getSocialType());
                String refreshToken = jwtService.createRefreshToken();
                userRepository.findBySocialTypeAndEmail(oAuth2User.getSocialType(), oAuth2User.getEmail())
                        .ifPresent(user -> {
                            user.updateRefreshToken(refreshToken);
                            userRepository.saveAndFlush(user);
                        });

                String redirectUrl = "https://moitda.site/auth/signup?accessToken=" + accessToken + "&refreshToken=" + refreshToken;

                response.sendRedirect(redirectUrl); // 프론트의 회원가입 추가 정보 입력 폼으로 리다이렉트

            } else {
                String accessToken = jwtService.createAccessToken(oAuth2User.getEmail(), oAuth2User.getSocialType());
                String refreshToken = jwtService.createRefreshToken();
                userRepository.findBySocialTypeAndEmail(oAuth2User.getSocialType(), oAuth2User.getEmail())
                        .ifPresent(user -> {
                            user.updateRefreshToken(refreshToken);
                            userRepository.saveAndFlush(user);
                        });

                String redirectUrl = "https://moitda.site/login?accessToken=" + accessToken + "&refreshToken=" + refreshToken;

                response.sendRedirect(redirectUrl); // 메인 페이지로 리다이렉트
            }
        } catch (Exception e) {
            throw e;
        }
    }
}
