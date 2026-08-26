package com.project.server.service.integration.kis;

import com.project.server.config.KisProperties;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.service.security.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KisCredentialResolver {

    private final KisProperties kisProperties;
    private final CryptoService cryptoService;

    public KisApiClient.KisCredential resolve(BrokerAccountEntity account) {
        boolean userKeys = notBlank(account.getAppKey()) && notBlank(account.getAppSecret());
        if (userKeys) {
            KisAccountParser.Parts parts = KisAccountParser.parse(
                    account.getAccountNumber(),
                    account.getAccountProductCode(),
                    null,
                    "01");
            KisAccountParser.requireCano(parts);
            return new KisApiClient.KisCredential(
                    cryptoService.decrypt(account.getAppKey()),
                    cryptoService.decrypt(account.getAppSecret()),
                    parts.cano(),
                    parts.productCode(),
                    KisApiClient.BrokerAccountCredentialSource.USER);
        }
        return envCredential(account.getAccountNumber(), account.getAccountProductCode());
    }

    public KisApiClient.KisCredential fromLinkRequest(BrokerAccountDto.LinkRequest request) {
        BrokerAccountDto.LinkRequest safe = request == null ? new BrokerAccountDto.LinkRequest() : request;
        boolean hasAppKey = notBlank(safe.getAppKey());
        boolean hasAppSecret = notBlank(safe.getAppSecret());
        if (hasAppKey ^ hasAppSecret) {
            throw ApiException.badRequest("appKey와 appSecret은 함께 전달해야 합니다.", "INCOMPLETE_KIS_CREDENTIALS");
        }
        if (hasAppKey) {
            KisAccountParser.Parts parts = KisAccountParser.parse(
                    safe.getAccountNumber(),
                    safe.getAccountProductCode(),
                    kisProperties.getMock().getCano(),
                    kisProperties.getMock().getAcntPrdtCd());
            KisAccountParser.requireCano(parts);
            return new KisApiClient.KisCredential(
                    safe.getAppKey().trim(),
                    safe.getAppSecret().trim(),
                    parts.cano(),
                    parts.productCode(),
                    KisApiClient.BrokerAccountCredentialSource.USER);
        }
        return envCredential(safe.getAccountNumber(), safe.getAccountProductCode());
    }

    public KisApiClient.KisCredential fromCredentialUpdate(
            BrokerAccountEntity account,
            BrokerAccountDto.CredentialsUpdateRequest request) {
        BrokerAccountDto.CredentialsUpdateRequest safe =
                request == null ? new BrokerAccountDto.CredentialsUpdateRequest() : request;
        boolean hasAppKey = notBlank(safe.getAppKey());
        boolean hasAppSecret = notBlank(safe.getAppSecret());
        if (hasAppKey ^ hasAppSecret) {
            throw ApiException.badRequest("appKey와 appSecret은 함께 전달해야 합니다.", "INCOMPLETE_KIS_CREDENTIALS");
        }

        String accountNumber = firstNonBlank(safe.getAccountNumber(), account.getAccountNumber());
        String productCode = firstNonBlank(safe.getAccountProductCode(), account.getAccountProductCode());

        if (hasAppKey) {
            KisAccountParser.Parts parts = KisAccountParser.parse(
                    accountNumber,
                    productCode,
                    kisProperties.getMock().getCano(),
                    kisProperties.getMock().getAcntPrdtCd());
            KisAccountParser.requireCano(parts);
            return new KisApiClient.KisCredential(
                    safe.getAppKey().trim(),
                    safe.getAppSecret().trim(),
                    parts.cano(),
                    parts.productCode(),
                    KisApiClient.BrokerAccountCredentialSource.USER);
        }
        return envCredential(accountNumber, productCode);
    }

    public boolean hasResolvableCredentials(BrokerAccountEntity account) {
        if (notBlank(account.getAppKey()) && notBlank(account.getAppSecret())) {
            return true;
        }
        return kisProperties.isStubMode() || envKeysConfigured();
    }

    public boolean envKeysConfigured() {
        return notBlank(kisProperties.getMock().getAppKey())
                && notBlank(kisProperties.getMock().getAppSecret());
    }

    private KisApiClient.KisCredential envCredential(String accountNumber, String productCode) {
        KisAccountParser.Parts parts = KisAccountParser.parse(
                accountNumber,
                productCode,
                defaultCano(),
                defaultProduct());
        KisAccountParser.requireCano(parts);

        if (kisProperties.isStubMode()) {
            return new KisApiClient.KisCredential(
                    firstNonBlank(kisProperties.getMock().getAppKey(), "stub-app-key"),
                    firstNonBlank(kisProperties.getMock().getAppSecret(), "stub-app-secret"),
                    parts.cano(),
                    parts.productCode(),
                    KisApiClient.BrokerAccountCredentialSource.ENV);
        }
        if (!envKeysConfigured()) {
            throw ApiException.badRequest(
                    "한투 모의투자 앱키가 없습니다. KIS_MOCK_APP_KEY/KIS_MOCK_APP_SECRET를 설정하거나 요청에 appKey/appSecret을 넣으세요.",
                    "MISSING_KIS_CREDENTIALS");
        }
        return new KisApiClient.KisCredential(
                kisProperties.getMock().getAppKey().trim(),
                kisProperties.getMock().getAppSecret().trim(),
                parts.cano(),
                parts.productCode(),
                KisApiClient.BrokerAccountCredentialSource.ENV);
    }

    private String defaultCano() {
        String cano = kisProperties.getMock().getCano();
        if (notBlank(cano)) {
            return cano.trim();
        }
        return kisProperties.isStubMode() ? "43123456" : null;
    }

    private String defaultProduct() {
        String product = kisProperties.getMock().getAcntPrdtCd();
        return notBlank(product) ? product.trim() : "01";
    }

    private static String firstNonBlank(String primary, String fallback) {
        return notBlank(primary) ? primary : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
