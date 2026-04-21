package com.example.auth.presentation.controller;

import com.example.auth.application.dto.RepublishSignupEventsResult;
import com.example.auth.application.service.UserSignupRepublishService;
import com.example.auth.presentation.dto.RepublishSignupEventsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부(운영자) 전용 컨트롤러.
 *
 * <p>경로 prefix {@code /api/internal/**}는 gateway-service에서 라우팅되지 않으므로
 * 외부 인터넷에서 직접 접근할 수 없다. 운영자는 kubectl port-forward 또는
 * 동등한 내부 경로를 통해 auth-service 파드에 직접 접근한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class AdminUserRepublishController {

    private final UserSignupRepublishService republishService;

    @PostMapping("/republish-signup-events")
    public RepublishSignupEventsResponse republishSignupEvents() {
        RepublishSignupEventsResult result = republishService.republishAll();
        return new RepublishSignupEventsResponse(
            result.totalUsers(),
            result.publishedCount(),
            result.failedCount()
        );
    }
}
