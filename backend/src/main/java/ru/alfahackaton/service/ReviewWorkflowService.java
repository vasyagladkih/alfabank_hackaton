package ru.alfahackaton.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.alfahackaton.dto.*;
import ru.alfahackaton.persistance.JsonDocuments;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.SnapshotDocument;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.persistance.repository.MutationRequestRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.risk.Assessment;
import ru.alfahackaton.risk.LoanTerms;
import ru.alfahackaton.risk.RiskEngine;
import ru.alfahackaton.risk.TermsEvaluation;
import ru.alfahackaton.service.exception.ApplicationConflictException;
import ru.alfahackaton.service.exception.ApplicationNotFoundException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Small transactional workflow over immutable evidence. No external requests and no snapshot rewriting. */
@Service
public class ReviewWorkflowService {
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final SnapshotService snapshots;
    private final EvidenceRepository evidence;
    private final MutationRequestRepository receipts;
    private final ApplicationMapper applicationMapper;
    private final EmployeeDossierMapper dossiers;
    private final RiskEngine engine;
    private final TransactionTemplate transactions;
    private final TransactionTemplate reads;
    private final Clock clock;

    public ReviewWorkflowService(ApplicationRepository applications, UserRepository users, SnapshotService snapshots,
                                 EvidenceRepository evidence, MutationRequestRepository receipts, ApplicationMapper applicationMapper,
                                 EmployeeDossierMapper dossiers, RiskEngine engine, PlatformTransactionManager transactionManager, Clock clock) {
        this.applications = applications;
        this.users = users;
        this.snapshots = snapshots;
        this.evidence = evidence;
        this.receipts = receipts;
        this.applicationMapper = applicationMapper;
        this.dossiers = dossiers;
        this.engine = engine;
        this.transactions = new TransactionTemplate(transactionManager);
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
        this.reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.clock = clock;
    }

    public List<ApplicationResponse> listApplications(ApplicationStatus status, String search, long actorId) {
        requireRole(actorId, "EMPLOYEE");
        if (search != null && search.length() > 120) throw new IllegalArgumentException("Поиск ограничен 120 символами");
        return applications.findAll(status == null ? null : status.getValue(), search == null ? null : search.strip())
                .stream().map(applicationMapper::toDto).toList();
    }

    public EmployeeDossier getDossier(long applicationId, long actorId) {
        return reads.execute(tx -> {
            requireRole(actorId, "EMPLOYEE");
            return dossier(applications.findById(applicationId).orElseThrow(ApplicationNotFoundException::new));
        });
    }

    public EmployeeDossier decide(long applicationId, EmployeeDecisionRequest request, long actorId) {
        String rationale = validate(request);
        String command = RiskJson.write(new DecisionCommand("EMPLOYEE_DECISION", applicationId, request.getExpectedVersion(),
                request.getType().name(), rationale, request.getOfferAmount()));
        String hash = JsonDocuments.sha256(command);
        return transactions.execute(tx -> {
            requireRole(actorId, "EMPLOYEE");
            var application = applications.lockById(applicationId).orElseThrow(ApplicationNotFoundException::new);
            var replay = replay(applicationId, actorId, request.getRequestId(), hash, EmployeeDossier.class);
            if (replay.isPresent()) return replay.get();
            requireState(application, request.getExpectedVersion(), "MANUAL_REVIEW");
            String nextStatus;
            String event;
            String summary;
            LoanTerms terms = null;
            BigDecimal payment = null;
            if (request.getType() == EmployeeDecisionType.REJECT) {
                nextStatus = "REJECTED";
                event = "EMPLOYEE_REJECTED";
                summary = "Сотрудник отклонил учебную заявку: " + rationale;
            } else {
                var stored = loadEvidence(applicationId);
                requireApplicable(application, stored);
                BigDecimal amount = request.getType() == EmployeeDecisionType.APPROVE
                        ? BigDecimal.valueOf(application.amount()) : BigDecimal.valueOf(request.getOfferAmount());
                if (request.getType() == EmployeeDecisionType.COUNTEROFFER
                        && (amount.compareTo(BigDecimal.valueOf(application.amount())) > 0
                        || amount.compareTo(stored.assessment().maxAffordableAmountRub()) > 0)) {
                    throw new IllegalArgumentException("Сумма предложения не должна превышать исходный запрос или доступный лимит");
                }
                terms = new LoanTerms(amount, application.termMonths(), stored.assessment().annualRatePercent());
                payment = validateTerms(stored, terms).monthlyPaymentRub();
                boolean approved = request.getType() == EmployeeDecisionType.APPROVE;
                nextStatus = approved ? "APPROVED" : "OFFER_PROPOSED";
                event = approved ? "EMPLOYEE_APPROVED" : "EMPLOYEE_COUNTEROFFERED";
                summary = (approved ? "Сотрудник одобрил учебную заявку: " : "Сотрудник предложил учебные условия: ") + rationale;
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            var changed = transition(application, nextStatus, summary, terms, payment, actorId, "EMPLOYEE",
                    request.getRequestId(), event, rationale, command, now);
            var response = dossier(changed);
            receipts.insert(applicationId, actorId, request.getRequestId(), hash, RiskJson.write(response), now);
            return response;
        });
    }

    public ApplicationResponse respondToOffer(long applicationId, OfferResponseRequest request, long actorId) {
        if (request == null || request.getRequestId() == null || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 0 || request.getDecision() == null) {
            throw new IllegalArgumentException("Нужны версия, ключ запроса и ответ на предложение");
        }
        String command = RiskJson.write(new OfferCommand("CLIENT_OFFER_RESPONSE", applicationId, request.getExpectedVersion(), request.getDecision().name()));
        String hash = JsonDocuments.sha256(command);
        return transactions.execute(tx -> {
            requireRole(actorId, "CLIENT");
            var application = applications.lockById(applicationId).orElseThrow(ApplicationNotFoundException::new);
            if (application.userId() != actorId) throw new AccessDeniedException("Доступ к чужой заявке запрещён");
            var replay = replay(applicationId, actorId, request.getRequestId(), hash, ApplicationResponse.class);
            if (replay.isPresent()) return replay.get();
            requireState(application, request.getExpectedVersion(), "OFFER_PROPOSED");
            boolean accepted = request.getDecision() == OfferResponseRequest.DecisionEnum.ACCEPT;
            LoanTerms terms = application.offeredAmount() == null || application.offeredTermMonths() == null || application.offeredAnnualRate() == null
                    ? null : new LoanTerms(application.offeredAmount(), application.offeredTermMonths(), application.offeredAnnualRate());
            BigDecimal payment = application.offeredMonthlyPayment();
            if (accepted) {
                var stored = loadEvidence(applicationId);
                requireApplicable(application, stored);
                var validation = validateTerms(stored, terms);
                if (payment == null || payment.compareTo(validation.monthlyPaymentRub()) != 0) {
                    throw conflict("Сохранённый платёж предложения не соответствует учебным правилам");
                }
            }
            String summary = accepted ? "Клиент принял учебное предложение; это демо-согласие без договора и выдачи."
                    : "Клиент отклонил учебное предложение; исходная рекомендация скоринга сохранена.";
            OffsetDateTime now = OffsetDateTime.now(clock);
            var changed = transition(application, accepted ? "APPROVED" : "REJECTED", summary, terms, payment, actorId, "CLIENT",
                    request.getRequestId(), accepted ? "CLIENT_OFFER_ACCEPTED" : "CLIENT_OFFER_REJECTED", summary, command, now);
            var response = applicationMapper.toDto(changed);
            receipts.insert(applicationId, actorId, request.getRequestId(), hash, RiskJson.write(response), now);
            return response;
        });
    }

    private ApplicationEntity transition(ApplicationEntity original, String status, String summary, LoanTerms terms, BigDecimal payment,
                                           long actorId, String actorRole, UUID requestId, String eventType, String rationale,
                                           String command, OffsetDateTime at) {
        if (!applications.transition(original.id(), original.version(), status, summary, terms == null ? null : terms.amountRub(),
                terms == null ? null : terms.termMonths(), terms == null ? null : terms.annualRatePercent(), payment, at)) {
            throw conflict("Версия заявки изменилась");
        }
        evidence.appendAudit(original.id(), "REQUEST:" + actorId + ":" + requestId, eventType, actorRole, actorId,
                original.status(), status, original.version() + 1, rationale, command, at);
        return applications.findById(original.id()).orElseThrow(ApplicationNotFoundException::new);
    }

    private EmployeeDossier dossier(ApplicationEntity application) {
        var stored = loadEvidence(application.id());
        return dossiers.toDto(application, stored.document(), stored.snapshot(), stored.assessment(),
                allowedActions(application, stored), evidence.findAudit(application.id()));
    }

    private StoredEvidence loadEvidence(long applicationId) {
        var document = evidence.findSnapshot(applicationId).orElse(null);
        ScoringSnapshot snapshot = null;
        if (document != null && "DEMO_INPUTS_V1".equals(document.schemaVersion())) {
            try {
                snapshot = snapshots.load(applicationId).orElse(null);
            } catch (tools.jackson.core.JacksonException unreadable) {
                // A valid immutable JSON document can still have an unsupported typed shape.
                // Keep its verified metadata visible and permit rejection; never substitute fresh inputs.
            }
        }
        var result = evidence.findResult(applicationId).orElse(null);
        return new StoredEvidence(document, snapshot, result == null ? null : RiskJson.read(result.payloadJson(), Assessment.class),
                result == null ? null : result.ruleVersion());
    }

    private List<EmployeeDecisionType> allowedActions(ApplicationEntity application, StoredEvidence stored) {
        if (!"MANUAL_REVIEW".equals(application.status())) return List.of();
        var allowed = new ArrayList<EmployeeDecisionType>();
        if (applicable(application, stored)) {
            var snapshot = stored.snapshot();
            var assessment = stored.assessment();
            if (engine.evaluateTerms(snapshot.inputs(), snapshot.request(),
                    new LoanTerms(snapshot.request().amountRub(), application.termMonths(), assessment.annualRatePercent())).allowed()) {
                allowed.add(EmployeeDecisionType.APPROVE);
            }
            BigDecimal cap = assessment.maxAffordableAmountRub();
            if (cap != null && cap.compareTo(engine.policy().product().minimumAmountRub()) >= 0
                    && engine.evaluateTerms(snapshot.inputs(), snapshot.request(),
                    new LoanTerms(cap.min(snapshot.request().amountRub()), application.termMonths(), assessment.annualRatePercent())).allowed()) {
                allowed.add(EmployeeDecisionType.COUNTEROFFER);
            }
        }
        allowed.add(EmployeeDecisionType.REJECT);
        return List.copyOf(allowed);
    }

    private boolean applicable(ApplicationEntity application, StoredEvidence stored) {
        var snapshot = stored.snapshot();
        var assessment = stored.assessment();
        return snapshot != null && snapshot.demoOnly() && snapshot.inputs() != null && snapshot.request() != null && assessment != null
                && engine.policy().version().equals(snapshot.ruleVersion()) && engine.policy().version().equals(stored.resultRuleVersion())
                && engine.policy().version().equals(assessment.ruleVersion()) && assessment.eligibleForManualApproval()
                && assessment.annualRatePercent() != null && assessment.maxAffordableAmountRub() != null && snapshot.inputs().asOf() != null
                && snapshot.inputs().asOf().equals(assessment.asOf()) && snapshot.request().amountRub() != null
                && snapshot.request().amountRub().compareTo(BigDecimal.valueOf(application.amount())) == 0
                && snapshot.request().termMonths() == application.termMonths();
    }

    private void requireApplicable(ApplicationEntity application, StoredEvidence stored) {
        if (!applicable(application, stored)) throw conflict("Нет применимых полных данных для одобрения или предложения по сохранённой версии правил");
    }

    private TermsEvaluation validateTerms(StoredEvidence stored, LoanTerms terms) {
        var validation = engine.evaluateTerms(stored.snapshot().inputs(), stored.snapshot().request(), terms);
        if (!validation.allowed()) throw conflict("Условия не проходят ограничения исходного запроса и доступной суммы");
        return validation;
    }

    private <T> Optional<T> replay(long applicationId, long actorId, UUID requestId, String hash, Class<T> type) {
        return receipts.find(applicationId, actorId, requestId).map(receipt -> {
            if (!receipt.commandSha256().equals(hash)) throw conflict("Ключ запроса уже использован для другой команды этой заявки");
            return RiskJson.read(receipt.responseJson(), type);
        });
    }

    private void requireRole(long actorId, String role) {
        if (!users.findById(actorId).map(user -> role.equals(user.role())).orElse(false)) throw new AccessDeniedException("Недостаточно прав для этой операции");
    }

    private static void requireState(ApplicationEntity application, long expectedVersion, String status) {
        if (application.version() != expectedVersion || !status.equals(application.status())) throw conflict("Состояние или версия заявки изменились; обновите карточку");
    }

    private static String validate(EmployeeDecisionRequest request) {
        if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || request.getRequestId() == null || request.getType() == null || request.getRationale() == null
                || request.getRationale().strip().length() < 3 || request.getRationale().length() > 1000) {
            throw new IllegalArgumentException("Нужны версия, ключ запроса, тип решения и причина от 3 до 1000 символов");
        }
        if (request.getType() == EmployeeDecisionType.COUNTEROFFER) {
            if (request.getOfferAmount() == null || request.getOfferAmount() < 100000 || request.getOfferAmount() > 50000000) {
                throw new IllegalArgumentException("Сумма встречного предложения должна соответствовать границам продукта");
            }
        } else if (request.getOfferAmount() != null) throw new IllegalArgumentException("Сумма предложения допустима только для COUNTEROFFER");
        return request.getRationale().strip();
    }

    private static ApplicationConflictException conflict(String message) { return new ApplicationConflictException(message); }
    private record StoredEvidence(SnapshotDocument document, ScoringSnapshot snapshot, Assessment assessment, String resultRuleVersion) {}
    private record DecisionCommand(String endpoint, long applicationId, long expectedVersion, String type, String rationale, Long offerAmount) {}
    private record OfferCommand(String endpoint, long applicationId, long expectedVersion, String decision) {}
}
