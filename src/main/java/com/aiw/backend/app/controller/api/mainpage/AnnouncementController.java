package com.aiw.backend.app.controller.api.mainpage;

import com.aiw.backend.app.model.announcement.dto.AnnouncementDTO;
import com.aiw.backend.app.model.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/announcements", produces = MediaType.APPLICATION_JSON_VALUE)
public class AnnouncementController {
    private final AnnouncementService announcementService;

    public AnnouncementController(final AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @Operation(
            summary = "팀 공지사항 생성",
            description = "팀장 권한을 가진 사용자가 새로운 팀 공지사항을 작성, 팀원들에게 알림을 발송."
    )
    public ResponseEntity<AnnouncementDTO> createAnnouncement(
            @RequestBody @Valid final AnnouncementDTO announcementDTO,
            @RequestParam(name = "memberId") final Long currentMemberId) {

        // DTO에 작성자 정보를 담아 서비스로 전달
        AnnouncementDTO created = announcementService.create(announcementDTO, currentMemberId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
