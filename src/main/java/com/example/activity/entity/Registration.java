package com.example.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 报名记录。两条唯一约束是并发正确性的根基：
 * <ul>
 *   <li>{@code uk_user_activity (user_id, activity_id)}：同一用户同一活动只能成功一次（业务去重）。</li>
 *   <li>{@code uk_user_request (user_id, request_id)}：请求键在用户作用域内唯一绑定（幂等）。</li>
 * </ul>
 * 只有成功提交的报名才会落库，因此失败请求天然不会占用 requestId。
 */
@Entity
@Table(name = "registration", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_activity", columnNames = {"user_id", "activity_id"}),
        @UniqueConstraint(name = "uk_user_request", columnNames = {"user_id", "request_id"})
})
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RegistrationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Registration() {
    }

    public Registration(Long activityId, Long userId, String requestId, LocalDateTime createdAt) {
        this.activityId = activityId;
        this.userId = userId;
        this.requestId = requestId;
        this.status = RegistrationStatus.REGISTERED;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
