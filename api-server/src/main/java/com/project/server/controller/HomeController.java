package com.project.server.controller;

import com.project.server.dto.HomeDto;
import com.project.server.service.home.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/home")
    public ResponseEntity<HomeDto.HomeResponse> getHome(
            @RequestParam(name = "user_id", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeService.getHome(userId));
    }
}
