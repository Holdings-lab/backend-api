package com.project.server.controller;

import com.project.server.dto.EventDto;
import com.project.server.service.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<EventDto.EventsResponse> getEvents(
            @RequestParam(name = "userId", defaultValue = "1") Long userId,
            @RequestParam(name = "dateSegment", defaultValue = "today") String dateSegment,
            @RequestParam(name = "category", defaultValue = "all") String category
    ) {
        return ResponseEntity.ok(eventService.getEvents(userId, dateSegment, category));
    }

    @PostMapping("/refresh")
    public ResponseEntity<EventDto.EventsResponse> refreshEvents(
            @RequestParam(name = "userId", defaultValue = "1") Long userId,
            @RequestParam(name = "dateSegment", defaultValue = "today") String dateSegment,
            @RequestParam(name = "category", defaultValue = "all") String category
    ) {
        return ResponseEntity.ok(eventService.refreshEvents(userId, dateSegment, category));
    }

        @PostMapping("/{eventId}/alerts")
    public ResponseEntity<EventDto.EventAlertResponse> updateEventAlert(
            @RequestParam(name = "userId", defaultValue = "1") Long userId,
            @PathVariable("eventId") Long eventId,
            @RequestBody EventDto.UpdateEventAlertRequest request
    ) {
        return ResponseEntity.ok(eventService.updateEventAlert(userId, eventId, request.isEnabled()));
    }
}
