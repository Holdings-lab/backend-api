package com.project.server.service.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {
    public String getUserGreeting(String user) { return user + "님, 오늘 시장에서 주목할 주요 시그널이 도착했습니다."; }
    public String getProfileInitial(String user) { return user.substring(0, 1); }
}
