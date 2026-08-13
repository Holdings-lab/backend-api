package com.project.server.controller;

import com.project.server.dto.EventDto;
import com.project.server.exception.ApiException;
import com.project.server.security.CurrentUserId;
import com.project.server.service.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /** 이벤트 목록 조회 */
    @GetMapping
    public ResponseEntity<EventDto.EventsResponse> getEvents(
            @CurrentUserId Long userId,
            @RequestParam(name = "dateSegment", defaultValue = "all") String dateSegment,
            @RequestParam(name = "category", defaultValue = "all") String category,
            HttpServletRequest request) {
        validateQueryParams(request, Set.of("dateSegment", "category"));
        return ResponseEntity.ok(eventService.getEvents(userId, dateSegment, category));
    }

    /** 날짜 세그먼트 목록 조회 */
    @GetMapping("/date-segments")
    public ResponseEntity<java.util.List<String>> getDateSegments(
            @CurrentUserId Long userId) {
        return ResponseEntity.ok(eventService.getDateSegments(userId));
    }

    /** 카테고리 목록 조회 */
    @GetMapping("/categories")
    public ResponseEntity<java.util.List<String>> getCategories(
            @CurrentUserId Long userId) {
        return ResponseEntity.ok(eventService.getCategories(userId));
    }

    /** 이벤트 아이템 목록 조회 */
    @GetMapping("/items")
    public ResponseEntity<java.util.List<EventDto.EventItem>> getEventItems(
            @CurrentUserId Long userId,
            @RequestParam(name = "dateSegment", defaultValue = "all") String dateSegment,
            @RequestParam(name = "category", defaultValue = "all") String category,
            HttpServletRequest request) {
        validateQueryParams(request, Set.of("dateSegment", "category"));
        return ResponseEntity.ok(eventService.getEventItems(userId, dateSegment, category));
    }

    /** 이벤트 알림 업데이트 */
    @PostMapping("/{eventId}/alerts")
    public ResponseEntity<EventDto.EventAlertResponse> updateEventAlert(
            @CurrentUserId Long userId,
            @PathVariable("eventId") Long eventId,
            @RequestBody EventDto.UpdateEventAlertRequest request) {
        return ResponseEntity.ok(eventService.updateEventAlert(userId, eventId, request.isEnabled()));
    }

    @GetMapping("/assets/{assetName}/policies")
    public ResponseEntity<EventDto.RelatedPoliciesResponse> getRelatedPoliciesByAsset(
            @CurrentUserId Long userId,
            @PathVariable("assetName") String assetName,
            @RequestParam(name = "dateSegment", defaultValue = "all") String dateSegment,
            @RequestParam(name = "category", defaultValue = "all") String category,
            HttpServletRequest request) {
        validateQueryParams(request, Set.of("dateSegment", "category"));
        return ResponseEntity.ok(eventService.getRelatedPoliciesByAsset(userId, assetName, dateSegment, category));
    }

    /** 정책 관련 자산 목록 조회 */
    @GetMapping("/policies/{eventId}/assets")
    public ResponseEntity<EventDto.RelatedAssetsResponse> getRelatedAssetsByPolicy(
            @CurrentUserId Long userId,
            @PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.getRelatedAssetsByPolicy(userId, eventId));
    }

    private void validateQueryParams(HttpServletRequest request, Set<String> allowedParams) {
        for (String parameterName : request.getParameterMap().keySet()) {
            if (!allowedParams.contains(parameterName)) {
                throw ApiException.badRequest(
                        "허용되지 않은 query param입니다: " + parameterName,
                        "EVENT_INVALID_QUERY_PARAM");
            }
        }
    }
}
