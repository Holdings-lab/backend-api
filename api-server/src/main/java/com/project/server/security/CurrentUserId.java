package com.project.server.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙여, 검증된 액세스 토큰에서 추출한 현재 사용자 ID를 주입한다.
 * 유효한 Bearer 토큰(사용자 JWT 또는 관리자 API 키 + X-User-Id)이 없으면 401 을 반환한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
