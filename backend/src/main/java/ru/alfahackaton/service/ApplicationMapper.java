package ru.alfahackaton.service;

import org.springframework.stereotype.Component;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.DecisionSummary;
import ru.alfahackaton.dto.LoanPurpose;
import ru.alfahackaton.dto.OfferTerms;
import ru.alfahackaton.persistance.entity.ApplicationEntity;

@Component
public class ApplicationMapper {
    public ApplicationResponse toDto(ApplicationEntity entity) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(entity.id());
        response.setAmount(entity.amount());
        response.setTermMonths(entity.termMonths());
        response.setPurpose(LoanPurpose.fromValue(entity.purpose()));
        response.setStatus(ApplicationStatus.fromValue(entity.status()));
        response.setCreatedAt(entity.createdAt());
        response.setUpdatedAt(entity.updatedAt());
        response.setCompanyName(entity.companyName());
        response.setInn(entity.inn());
        response.setRejectionReason(entity.rejectionReason());
        response.setVersion(entity.version());
        if (entity.decisionSummary() != null && entity.ruleVersion() != null && entity.assessedAt() != null) {
            DecisionSummary summary = new DecisionSummary();
            summary.setRuleVersion(entity.ruleVersion());
            summary.setAssessedAt(entity.assessedAt());
            summary.setExplanation(entity.decisionSummary());
            summary.setDemoOnly(true);
            response.setDecisionSummary(summary);
        }
        if (entity.offeredAmount() != null) {
            OfferTerms offer = new OfferTerms();
            offer.setAmount(entity.offeredAmount().longValueExact());
            offer.setTermMonths(entity.offeredTermMonths());
            offer.setAnnualRate(entity.offeredAnnualRate().stripTrailingZeros().toPlainString());
            offer.setMonthlyPayment(entity.offeredMonthlyPayment().setScale(2).toPlainString());
            response.setOffer(offer);
        }
        return response;
    }
}
