package com.project.server.service;

import com.project.server.dto.PolicyEventResponse;
import com.project.server.repository.InMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final InMemoryRepository repository;

    public List<PolicyEventResponse> getAllEvents() {
        return repository.findAllEvents().stream()
                .map(PolicyEventResponse::from)
                .collect(Collectors.toList());
    }
}
