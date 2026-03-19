package com.project.server.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.server.config.props.RepositoryProperties;
import com.project.server.domain.PolicyEvent;
import com.project.server.domain.Subscription;
import com.project.server.domain.User;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class InMemoryRepository {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    private final String EVENTS_FILE;
    private final String USERS_FILE;
    private final ObjectMapper objectMapper;

    public InMemoryRepository(RepositoryProperties properties) {
        this.EVENTS_FILE = properties.getEventsFile();
        this.USERS_FILE = properties.getUsersFile();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
    }

    @PostConstruct
    public void initDummyData() {
        // 1. 유저 데이터 파일에서 로드
        loadUsersFromFile();

        // 2. 검색/구독 키워드 매핑 (In-Memory 유지) - 임시로 모든 유저가 모든 키워드 구독하도록 설정
        for (User user : users.values()) {
            subscriptions.add(new Subscription((long) subscriptions.size() + 1, user.getId(), "금리인상"));
            subscriptions.add(new Subscription((long) subscriptions.size() + 1, user.getId(), "부동산"));
            subscriptions.add(new Subscription((long) subscriptions.size() + 1, user.getId(), "환율"));
        }

        // 3. 서버 초기 실행 시 JSON 파일이 없거나 비어있다면 더미 이벤트 데이터 셋업
        File file = new File(EVENTS_FILE);
        if (!file.exists() || file.length() == 0) {
            try {
                file.getParentFile().mkdirs();
                List<PolicyEvent> initialEvents = new ArrayList<>();
                initialEvents.add(new PolicyEvent(1L, "한국은행 기준금리 0.25%p 인상", "금리인상", 8.5, "대출 이자 부담 증가 및 소비 위축 예상",
                        LocalDateTime.now().minusDays(1)));
                initialEvents.add(new PolicyEvent(2L, "부동산 규제 완화 정책 발표", "부동산", 7.2, "주택 거래량 일시적 증가 예상",
                        LocalDateTime.now().minusHours(5)));
                objectMapper.writeValue(file, initialEvents);
                log.info("초기 Dummy Event 데이터를 '{}'에 생성했습니다.", EVENTS_FILE);
            } catch (IOException e) {
                log.error("초기 데이터 생성 실패", e);
            }
        }
    }

    // 특정 키워드를 구독하는 User 목록 조회
    public List<User> findUsersByKeyword(String keyword) {
        List<Long> userIds = subscriptions.stream()
                .filter(sub -> sub.getKeyword().equals(keyword))
                .map(Subscription::getUserId)
                .collect(Collectors.toList());

        return userIds.stream()
                .map(users::get)
                .filter(user -> user != null && user.getFcmToken() != null)
                .collect(Collectors.toList());
    }

    // JSON 파일에서 전체 이벤트 읽어오기
    public List<PolicyEvent> findAllEvents() {
        File file = new File(EVENTS_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(file, new TypeReference<List<PolicyEvent>>() {
            });
        } catch (IOException e) {
            log.error("JSON 파일 읽기 실패", e);
            return new ArrayList<>();
        }
    }

    // ID 기반 조회
    public PolicyEvent findEventById(Long id) {
        return findAllEvents().stream()
                .filter(event -> event.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Java 쪽에서도 저장이 필요할 경우를 대비한 메서드
    public void saveEvent(PolicyEvent event) {
        List<PolicyEvent> events = findAllEvents();
        events.removeIf(e -> e.getId().equals(event.getId())); // 덮어쓰기 방지
        events.add(event);
        try {
            objectMapper.writeValue(new File(EVENTS_FILE), events);
        } catch (IOException e) {
            log.error("JSON 파일 쓰기 실패", e);
        }
    }

    // 유저 데이터 파일에서 로드
    private void loadUsersFromFile() {
        File file = new File(USERS_FILE);
        if (file.exists()) {
            try {
                List<User> userList = objectMapper.readValue(file, new TypeReference<List<User>>() {
                });
                for (User user : userList) {
                    users.put(user.getId(), user);
                }
                log.info("유저 데이터를 '{}'에서 로드했습니다. 총 {}명", USERS_FILE, users.size());
            } catch (IOException e) {
                log.error("유저 데이터 로드 실패", e);
            }
        } else {
            log.info("유저 데이터 파일이 존재하지 않습니다: {}", USERS_FILE);
        }
    }

    // 유저 저장
    public void saveUser(User user) {
        users.put(user.getId(), user);
        saveUsersToFile();
    }

    // 유저 삭제
    public void deleteUserById(Long id) {
        if (users.remove(id) != null) {
            saveUsersToFile();
        }
    }

    // 유저명으로 찾기
    public User findUserByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    // ID로 유저 조회
    public User findUserById(Long id) {
        return users.get(id);
    }

    // 모든 유저 조회
    public List<User> findAllUsers() {
        return new ArrayList<>(users.values());
    }

    // 유저 데이터를 파일에 저장
    private void saveUsersToFile() {
        try {
            File file = new File(USERS_FILE);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            List<User> userList = new ArrayList<>(users.values());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(USERS_FILE), userList);

            log.info("유저 데이터를 '{}'에 저장했습니다.", USERS_FILE);
        } catch (IOException e) {
            log.error("유저 데이터 저장 실패", e);
        }
    }
}
