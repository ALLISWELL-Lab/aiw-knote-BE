package com.aiw.backend.app.controller.api.mainpage;

import com.aiw.backend.app.model.notification.dto.NotificationDTO;
import com.aiw.backend.app.model.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(value = "/api/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(final NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "전체 알림 조회", description = "시스템에 기록된 모든 알림 및 설정 데이터를 조회합니다.")
    public ResponseEntity<List<NotificationDTO>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "특정 알림 상세 조회", description = "알림 ID를 기반으로 특정 알림의 상세 정보를 조회합니다.")
    public ResponseEntity<NotificationDTO> getNotification(
            @PathVariable(name = "id") final Long id) {
        return ResponseEntity.ok(notificationService.get(id));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    @Operation(summary = "알림 생성", description = "새로운 알림 데이터를 수동으로 생성합니다.")
    public ResponseEntity<Long> createNotification(
            @RequestBody @Valid final NotificationDTO notificationDTO) {
        final Long createdId = notificationService.create(notificationDTO);
        return new ResponseEntity<>(createdId, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "알림 정보 수정", description = "기존 알림의 내용이나 상태 정보를 업데이트합니다.")
    public ResponseEntity<Long> updateNotification(@PathVariable(name = "id") final Long id,
            @RequestBody @Valid final NotificationDTO notificationDTO) {
        notificationService.update(id, notificationDTO);
        return ResponseEntity.ok(id);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    @Operation(summary = "알림 삭제", description = "특정 알림 데이터를 시스템에서 삭제합니다.")
    public ResponseEntity<Void> deleteNotification(@PathVariable(name = "id") final Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
