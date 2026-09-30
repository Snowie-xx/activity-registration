package com.example.activity.service;

import com.example.activity.dto.PageResult;
import com.example.activity.dto.RegistrationMapper;
import com.example.activity.dto.RegistrationResponse;
import com.example.activity.entity.Registration;
import com.example.activity.exception.BusinessException;
import com.example.activity.exception.ErrorCode;
import com.example.activity.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 报名「编排层」，自身不在事务中。职责：
 * <ol>
 *   <li>把写路径委托给 {@link RegistrationTx} 的事务核心；</li>
 *   <li>捕获并发下唯一键冲突（DataIntegrityViolationException），在独立事务中
 *       解析为幂等重放 / 参数冲突 / 业务去重，绝不向客户端泄露数据库异常；</li>
 *   <li>提供当前用户的报名查询（严格按 X-User-Id 隔离）。</li>
 * </ol>
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationTx tx;
    private final RegistrationRepository registrationRepository;

    public RegistrationService(RegistrationTx tx, RegistrationRepository registrationRepository) {
        this.tx = tx;
        this.registrationRepository = registrationRepository;
    }

    public RegistrationResult register(long userId, long activityId, String requestId) {
        try {
            return tx.register(userId, activityId, requestId);
        } catch (DataIntegrityViolationException e) {
            return resolveConflict(userId, activityId, requestId);
        }
    }

    /**
     * 唯一键冲突解析。此刻事务核心已回滚，这里在各自独立事务中读取已提交记录，
     * 按业务优先级解析为：同键同活动 → 重放；同键异活动 → 参数冲突；同活动换键 → 业务去重。
     */
    private RegistrationResult resolveConflict(long userId, long activityId, String requestId) {
        Optional<Registration> byKey = registrationRepository.findByUserIdAndRequestId(userId, requestId);
        if (byKey.isPresent()) {
            Registration existing = byKey.get();
            if (existing.getActivityId().longValue() == activityId) {
                return RegistrationResult.replay(existing);
            }
            throw BusinessException.idempotencyConflict();
        }
        Optional<Registration> byActivity = registrationRepository.findByUserIdAndActivityId(userId, activityId);
        if (byActivity.isPresent()) {
            throw BusinessException.alreadyRegistered();
        }
        log.error("unexpected duplicate key with no matching record: userId={} activityId={} requestId={}",
                userId, activityId, requestId);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "registration conflict, please retry");
    }

    public PageResult<RegistrationResponse> list(long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Registration> p = registrationRepository.findByUserIdOrdered(userId, pageable);
        List<RegistrationResponse> items = p.getContent().stream().map(RegistrationMapper::toResponse).toList();
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    public RegistrationResponse detail(long userId, long id) {
        Registration r = registrationRepository.findById(id)
                .filter(x -> x.getUserId().longValue() == userId)
                .orElseThrow(BusinessException::registrationNotFound);
        return RegistrationMapper.toResponse(r);
    }
}
