package com.ahn.settlement.auth;

import com.ahn.settlement.exception.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class AuthValidator {

    public void validateCreatorAccess(String userId, String userRole, String creatorId) {
        if (!"CREATOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
        if ("CREATOR".equals(userRole) && !userId.equals(creatorId)) {
            throw new ForbiddenException("본인의 정산 내역만 조회할 수 있습니다.");
        }
    }

    public void validateAdminAccess(String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ForbiddenException("운영자만 접근할 수 있습니다.");
        }
    }
}
