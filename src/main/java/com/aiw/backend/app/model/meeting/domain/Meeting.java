package com.aiw.backend.app.model.meeting.domain;

import com.aiw.backend.app.model.project.domain.Project;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


@Entity
@Table(name = "Meetings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Meeting {

  @Id
  @Column(nullable = false, updatable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String agenda;

  @Column(nullable = false)
  private LocalDateTime scheduledAt;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  @Column(nullable = true) // 회의 진행 중일 땐 없는 흐름이 더 자연스러우니까
  private LocalDateTime endedAt;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String createdType;

//  @Column(name = "transcript_path") // DB에는 파일이 위치한 경로만 저장
//  private String transcriptPath; // 1st 데모 이후 사용 예정

  @Column(columnDefinition = "TEXT")
  private String transcript; // STT 원문 저장

  @Column(columnDefinition = "TEXT") // 요약은 검색/조회용으로 DB에 바로 저장
  private String aiSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @Column(nullable = false, columnDefinition = "tinyint", length = 1)
  private Boolean activated = true;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private OffsetDateTime lastUpdatedAt;

}
