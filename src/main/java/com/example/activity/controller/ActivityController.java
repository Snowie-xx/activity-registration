package com.example.activity.controller;

import com.example.activity.dto.ActivityResponse;
import com.example.activity.dto.ApiResponse;
import com.example.activity.dto.PageResult;
import com.example.activity.exception.BusinessException;
import com.example.activity.service.ActivityService;
import com.example.activity.web.PageParams;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<PageResult<ActivityResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageParams.validate(page, size);
        return ApiResponse.ok(activityService.list(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityResponse> detail(@PathVariable long id) {
        if (id <= 0) {
            throw BusinessException.invalidArgument("activityId must be a positive integer");
        }
        return ApiResponse.ok(activityService.detail(id));
    }
}
