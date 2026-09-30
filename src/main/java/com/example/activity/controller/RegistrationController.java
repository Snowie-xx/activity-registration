package com.example.activity.controller;

import com.example.activity.dto.ApiResponse;
import com.example.activity.dto.PageResult;
import com.example.activity.dto.RegistrationMapper;
import com.example.activity.dto.RegistrationRequest;
import com.example.activity.dto.RegistrationResponse;
import com.example.activity.exception.BusinessException;
import com.example.activity.service.RegistrationResult;
import com.example.activity.service.RegistrationService;
import com.example.activity.web.PageParams;
import com.example.activity.web.RequestId;
import com.example.activity.web.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegistrationResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestBody RegistrationRequest body) {
        // 先校验身份标记，再校验参数（活动 ID、请求键）
        long userId = UserId.parse(userIdHeader);
        if (body == null || body.getActivityId() == null || body.getActivityId() <= 0) {
            throw BusinessException.invalidArgument("activityId must be a positive integer");
        }
        RequestId.validate(body.getRequestId());

        RegistrationResult result = registrationService.register(userId, body.getActivityId(), body.getRequestId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(RegistrationMapper.toResponse(result.registration())));
    }

    @GetMapping
    public ApiResponse<PageResult<RegistrationResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long userId = UserId.parse(userIdHeader);
        PageParams.validate(page, size);
        return ApiResponse.ok(registrationService.list(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RegistrationResponse> detail(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable long id) {
        long userId = UserId.parse(userIdHeader);
        if (id <= 0) {
            throw BusinessException.invalidArgument("registration id must be a positive integer");
        }
        return ApiResponse.ok(registrationService.detail(userId, id));
    }
}
