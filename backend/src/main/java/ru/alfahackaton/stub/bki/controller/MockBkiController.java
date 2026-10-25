package ru.alfahackaton.stub.bki.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.models.BkiDto;
import ru.alfahackaton.stub.bki.repository.MockBkiRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stubs/bki")
public class MockBkiController {

    private final MockBkiRepository repository;

    public MockBkiController(MockBkiRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/report")
    public ResponseEntity<BkiDto> report(@RequestBody Map<String, String> request) {
        String inn = request.get("inn");
        if (inn == null || inn.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return repository.findById(inn)
                .map(entity -> ResponseEntity.ok(new BkiDto(
                        entity.inn(),
                        entity.bkiScore(),
                        entity.totalMonthlyPaymentRub(),
                        entity.currentOverdueDays(),
                        entity.cntOverdueOver90d()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
