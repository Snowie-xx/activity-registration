package com.example.activity.dto;

/**
 * POST /api/registrations 请求体。
 */
public class RegistrationRequest {

    private Long activityId;
    private String requestId;

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
