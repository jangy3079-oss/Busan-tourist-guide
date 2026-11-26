package kr.busan.safetrip.emergency.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * [긴급 담당] 상황판 저장 이벤트 비동기 리스너 활성화.
 */
@Configuration
@EnableAsync
public class DashboardAsyncConfig {}
