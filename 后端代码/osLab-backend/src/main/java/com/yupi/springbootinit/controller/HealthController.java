package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight process liveness probe for the deployment health check.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * Returns only whether this application process can receive HTTP requests.
     *
     * <p>This endpoint deliberately does not check MySQL, Redis, Neo4j, or
     * Elasticsearch. Docker Compose waits for those services independently;
     * reporting their state here would turn a downstream outage into a false
     * process restart.</p>
     */
    @GetMapping("/liveness")
    public BaseResponse<Map<String, String>> liveness() {
        return ResultUtils.success(Collections.singletonMap("status", "alive"));
    }
}
