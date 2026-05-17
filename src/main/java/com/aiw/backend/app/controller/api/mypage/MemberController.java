package com.aiw.backend.app.controller.api.mypage;

import com.aiw.backend.app.model.member.dto.MemberDTO;
import com.aiw.backend.app.model.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping(value = "/api/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberController {

    private final MemberService memberService;

    public MemberController(final MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @Operation(summary = "전체 회원 조회", description = "시스템에 등록된 모든 회원 목록을 조회합니다.")
    public ResponseEntity<List<MemberDTO>> getAllMembers() {
        return ResponseEntity.ok(memberService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "특정 회원 상세 조회", description = "회원 ID를 기반으로 특정 회원의 정보를 조회합니다.")
    public ResponseEntity<MemberDTO> getMember(@PathVariable(name = "id") final Long id) {
        return ResponseEntity.ok(memberService.get(id));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    @Operation(summary = "신규 회원 생성", description = "새로운 회원 정보를 등록합니다.")
    public ResponseEntity<MemberDTO> createMember(@RequestBody @Valid final MemberDTO memberDTO) {
        final MemberDTO createdMember = memberService.create(memberDTO);
        return new ResponseEntity<>(createdMember, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "회원 정보 수정", description = "기존 회원의 전체 정보를 업데이트합니다.")
    public ResponseEntity<Long> updateMember(@PathVariable(name = "id") final Long id,
            @RequestBody @Valid final MemberDTO memberDTO) {
        memberService.update(id, memberDTO);
        return ResponseEntity.ok(id);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    @Operation(summary = "회원 탈퇴/삭제", description = "특정 회원 데이터를 시스템에서 삭제합니다.")
    public ResponseEntity<Void> deleteMember(@PathVariable(name = "id") final Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    //마이페이지: 내 정보 조회
    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "마이페이지에서 현재 로그인한 사용자의 정보를 조회합니다.")
    public ResponseEntity<MemberDTO> getMyInfo(@RequestParam(name = "memberId") Long memberId) {
        return ResponseEntity.ok(memberService.getShowInfo(memberId));
    }

    //마이페이지: 내 정보 수정
    @PostMapping("/me")
    @Operation(summary = "내 정보 수정", description = "마이페이지에서 사용자의 이름 및 관심 분야를 수정합니다.")
    public ResponseEntity<MemberDTO> updateMyInfo(
            @RequestParam(name = "memberId") Long memberId,
            @RequestBody @Valid final MemberDTO memberDTO) {
        memberService.updateMyInfo(memberId, memberDTO);
        return ResponseEntity.ok(MemberDTO.builder().message("Success").build());
    }

}
