package com.project.server.controller;

import com.project.server.dto.ApiResponse;
import com.project.server.dto.NewsroomDto;
import com.project.server.security.CurrentUserId;
import com.project.server.service.newsroom.NewsroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/newsroom")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NewsroomController {

    private final NewsroomService newsroomService;

    @GetMapping
    public ResponseEntity<?> getNewsroom(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String briefingDate
    ) {
        try {
            return ResponseEntity.ok(newsroomService.getNewsroom(userId, briefingDate));
        } catch (NewsroomService.NewsroomUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("NEWSROOM_UNAVAILABLE", ex.getMessage(), ex.getResult()));
        }
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<NewsroomDto.DetailResponse> getNewsroomDetail(
            @CurrentUserId Long userId,
            @PathVariable String ticker,
            @RequestParam(required = false) String briefingDate
    ) {
        return ResponseEntity.ok(newsroomService.getNewsroomDetail(userId, ticker, briefingDate));
    }
}
