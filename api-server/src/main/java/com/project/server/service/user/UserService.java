package com.project.server.service.user;

import com.project.server.domain.UserEntity;
import com.project.server.repository.UserJpaRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    private final UserJpaRepository userJpaRepository;
    
    public UserService(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }
    
    public String getUserGreeting(Long userId) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);
        String name = (user != null && user.getNickname() != null) ? user.getNickname() : "사용자";
        return name + "님, 오늘 시장에서 주목할 주요 시그널이 도착했습니다.";
    }
    
    public String getProfileInitial(Long userId) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);
        if (user == null) return "U";
        if (user.getNickname() != null && !user.getNickname().isEmpty()) {
            return user.getNickname().substring(0, 1).toUpperCase();
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            return user.getEmail().substring(0, 1).toUpperCase();
        }
        return "U";
    }
}
