package com.lms.backend.controller;

import com.lms.backend.dto.ZoomMeetingResponse;
import com.lms.backend.service.ZoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/zoom")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ZoomController {

    private final ZoomService zoomService;

    public ZoomController(ZoomService zoomService) {
        this.zoomService = zoomService;
    }

    @PostMapping("/meetings")
    public ResponseEntity<ZoomMeetingResponse> createMeeting(@RequestBody Map<String, Object> payload) {
        String topic = (String) payload.getOrDefault("topic", "Live Class Session");
        int duration = 60;
        
        if (payload.containsKey("duration")) {
            Object durationObj = payload.get("duration");
            if (durationObj instanceof Integer) {
                duration = (Integer) durationObj;
            } else if (durationObj instanceof String) {
                duration = Integer.parseInt((String) durationObj);
            }
        }

        ZoomMeetingResponse response = zoomService.createMeeting(topic, duration);
        return ResponseEntity.ok(response);
    }
}
