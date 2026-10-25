package ru.alfahackaton.stub.fns.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.models.FnsDto;
import ru.alfahackaton.stub.fns.repository.MockFnsRepository;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stubs/fns")
public class MockFnsController {

    private final MockFnsRepository repository;

    public MockFnsController(MockFnsRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/check")
    public ResponseEntity<FnsDto> check(@RequestBody Map<String, String> request) {
        String inn = request.get("inn");
        if (inn == null || inn.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return repository.findById(inn)
                .map(entity -> {
                    LocalDate now = LocalDate.now();
                    LocalDate regDate = entity.registrationDate() != null ? entity.registrationDate() : now;
                    Period period = Period.between(regDate, now);
                    int ageMonths = Math.max(1, period.getYears() * 12 + period.getMonths());

                    return ResponseEntity.ok(new FnsDto(
                            entity.inn(),
                            entity.companyName(),
                            entity.stateStatus(),
                            entity.taxBlocksExist(),
                            ageMonths
                    ));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
