package com.project.server.service;

import com.project.server.domain.User;
import com.project.server.dto.AuthDto;
import com.project.server.repository.InMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final InMemoryRepository repository;
    private final AtomicLong userIdGenerator = new AtomicLong(1);

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        // 중복 사용자명 체크
        if (repository.findUserByUsername(request.getUsername()) != null) {
            throw new RuntimeException("이미 존재하는 사용자명입니다.");
        }

        // 새 사용자 생성
        Long userId = userIdGenerator.getAndIncrement();
        User newUser = User.builder()
                .id(userId)
                .username(request.getUsername())
                .password(request.getPassword()) // 실제로는 암호화해야 함
                .build();

        repository.saveUser(newUser);

        log.info("새 사용자 등록: {}", request.getUsername());

        return AuthDto.AuthResponse.builder()
                .userId(userId)
                .username(request.getUsername())
                .message("회원가입이 완료되었습니다.")
                .build();
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        User user = repository.findUserByUsername(request.getUsername());

        if (user == null) {
            throw new RuntimeException("존재하지 않는 사용자입니다.");
        }

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        log.info("사용자 로그인: {}", request.getUsername());

        return AuthDto.AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .message("로그인 성공")
                .build();
    }

    public AuthDto.AuthResponse registerFCMToken(AuthDto.FCMTokenRequest request) {
        // 입력 검증
        if (request.getUserId() == null) {
            throw new RuntimeException("사용자 ID가 필요합니다.");
        }
        if (request.getFcmToken() == null || request.getFcmToken().trim().isEmpty()) {
            throw new RuntimeException("FCM 토큰이 유효하지 않습니다.");
        }

        User user = repository.findUserById(request.getUserId());

        if (user == null) {
            throw new RuntimeException("존재하지 않는 사용자입니다.");
        }

        // FCM 토큰 업데이트
        user.setFcmToken(request.getFcmToken());
        repository.saveUser(user);

        log.info("FCM 토큰 등록: 사용자={}, 토큰={}...", user.getUsername(), request.getFcmToken().substring(0, 50));

        return AuthDto.AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .message("FCM 토큰이 등록되었습니다.")
                .build();
    }

    public AuthDto.AuthResponse deleteAccount(Long userId) {
        User user = repository.findUserById(userId);

        if (user == null) {
            throw new RuntimeException("존재하지 않는 사용자입니다.");
        }

        repository.deleteUserById(userId);
        log.info("사용자 탈퇴: {}", user.getUsername());

        return AuthDto.AuthResponse.builder()
                .userId(userId)
                .username(user.getUsername())
                .message("회원 탈퇴가 완료되었습니다.")
                .build();
    }

    public List<AuthDto.AccountInfo> getAllAccounts() {
        List<User> users = repository.findAllUsers();
        return users.stream()
                .map(user -> AuthDto.AccountInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .build())
                .collect(Collectors.toList());
    }
}