package com.project.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private final Mock mock = new Mock();
    private final Api api = new Api();
    private final Sync sync = new Sync();

    /** real이 아니면 모의투자(paper) 엔드포인트 */
    public boolean isPaperMode() {
        return !"real".equalsIgnoreCase(api.getMode());
    }

    @Getter
    @Setter
    public static class Mock {
        /** KIS_MOCK_APP_KEY */
        private String appKey = "";
        /** KIS_MOCK_APP_SECRET */
        private String appSecret = "";
        /** KIS_MOCK_CANO — 계좌번호 앞 8자리 */
        private String cano = "";
        /** KIS_MOCK_ACNT_PRDT_CD — 계좌상품코드 2자리 */
        private String acntPrdtCd = "01";
    }

    @Getter
    @Setter
    public static class Api {
        /** real | paper */
        private String mode = "paper";
        private String realBaseUrl = "https://openapi.koreainvestment.com:9443";
        private String paperBaseUrl = "https://openapivts.koreainvestment.com:29443";
        private long timeoutSeconds = 12;
        private int maxRetries = 3;
    }

    @Getter
    @Setter
    public static class Sync {
        private String scheduleCron = "0 0 12,18 * * *";
        private boolean globalScheduleEnabled = false;
    }
}
