package com.project.server.service.asset;

import com.project.server.domain.asset.Status;
import com.project.server.domain.asset.InvestmentHorizon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class BriefingMessageComposer {

    public String compose(Status status, BigDecimal drawdownPct, InvestmentHorizon horizon) {
        if (status == Status.NORMAL) {
            return null;
        }

        int drawdownAbs = drawdownPct.abs().setScale(0, RoundingMode.HALF_UP).intValue();
        String guidance = guidanceFor(horizon);
        return "포트폴리오가 최근 고점 대비 " + drawdownAbs + "% 하락했어요. " + guidance;
    }

    private String guidanceFor(InvestmentHorizon horizon) {
        return switch (horizon) {
            case UNDER_1Y ->
                    "자금 사용 시점이 가까워 회복을 기다릴 시간이 부족할 수 있으니, 필요한 자금이 투자되어 있지 않은지 확인해보세요.";
            case ONE_TO_THREE_YEARS ->
                    "중기 계획 안에서 지켜볼 만한 변화라, 계획에 변화가 없는지 정도만 가볍게 살펴보세요.";
            case THREE_TO_FIVE_YEARS ->
                    "지금 속도라면 계획엔 큰 영향이 없을 가능성이 높아, 당장 조치가 필요하진 않아요.";
            case OVER_FIVE_YEARS ->
                    "장기 계획엔 영향이 제한적일 수 있어, 평소처럼 유지하셔도 괜찮아요.";
        };
    }
}
