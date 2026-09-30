package com.example.activity.service;

import com.example.activity.dto.ActivityBasicInfo;
import com.example.activity.dto.ActivityResponse;
import com.example.activity.dto.PageResult;
import com.example.activity.entity.Activity;
import com.example.activity.exception.BusinessException;
import com.example.activity.repository.ActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityCacheService cache;

    public ActivityService(ActivityRepository activityRepository, ActivityCacheService cache) {
        this.activityRepository = activityRepository;
        this.cache = cache;
    }

    /**
     * 活动列表：包含关闭与零名额活动，按 ID 升序。
     * 剩余名额必须实时，因此列表直接读库，不走缓存（缓存只覆盖详情的基础信息）。
     */
    public PageResult<ActivityResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Activity> p = activityRepository.findAll(pageable);
        List<ActivityResponse> items = p.getContent().stream().map(this::toResponse).toList();
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    /**
     * 活动详情：基础信息（标题/状态/总名额）走缓存，剩余名额实时读 MySQL。
     * 缓存命中时用轻量查询只取余量；未命中时整行读取并回填缓存。
     */
    public ActivityResponse detail(long id) {
        ActivityBasicInfo info = cache.get(id).orElse(null);
        if (info != null) {
            Integer remaining = activityRepository.findRemainingQuota(id);
            if (remaining == null) {
                throw BusinessException.activityNotFound();
            }
            return new ActivityResponse(info.id(), info.title(), info.status(), info.totalQuota(), remaining);
        }

        Activity a = activityRepository.findById(id).orElseThrow(BusinessException::activityNotFound);
        ActivityBasicInfo basic = new ActivityBasicInfo(a.getId(), a.getTitle(), a.getStatus().name(), a.getTotalQuota());
        cache.put(id, basic);
        return toResponse(a);
    }

    private ActivityResponse toResponse(Activity a) {
        return new ActivityResponse(a.getId(), a.getTitle(), a.getStatus().name(),
                a.getTotalQuota(), a.getRemainingQuota());
    }
}
