package ru.alfahackaton.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.alfahackaton.dto.PaymentSchedule;
import ru.alfahackaton.dto.PaymentScheduleItem;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentScheduleServiceTest {

    private final PaymentScheduleService service = new PaymentScheduleService();

    @Test
    @DisplayName("Аннуитетный график: точные суммы платежа, переплаты и сходимость остатка в 0")
    void testScheduleStrictValuesAndZeroBalance() {
        long amount = 1_500_000L;
        int termMonths = 12;
        double rate = 18.5;
        LocalDate startDate = LocalDate.of(2026, 11, 1);

        PaymentSchedule schedule = service.calculateSchedule(amount, termMonths, rate, startDate);

        assertNotNull(schedule);
        assertEquals(termMonths, schedule.getItems().size());

        // Точный аннуитетный расчет: i = 0.185 / 12
        assertEquals(137_877L, schedule.getMonthlyPayment());
        assertEquals(154_527L, schedule.getTotalInterest());
        assertEquals(1_654_527L, schedule.getTotalPayout());

        long sumPrincipal = 0L;
        for (int i = 0; i < schedule.getItems().size(); i++) {
            PaymentScheduleItem item = schedule.getItems().get(i);
            sumPrincipal += item.getPrincipalPayment();
            assertTrue(item.getTotalPayment() > 0);
            assertTrue(item.getInterestPayment() >= 0);
            // Инвариант: платеж = тело + проценты
            assertEquals(item.getTotalPayment(), item.getPrincipalPayment() + item.getInterestPayment());
            // До последнего месяца платеж строго равен фиксированному аннуитету
            if (i < schedule.getItems().size() - 1) {
                assertEquals(schedule.getMonthlyPayment(), item.getTotalPayment());
            }
        }

        // Сумма всех выплат тела равна первоначальной сумме кредита
        assertEquals(amount, sumPrincipal);

        // Остаток долга после последнего платежа строго равен 0
        PaymentScheduleItem lastItem = schedule.getItems().get(termMonths - 1);
        assertEquals(0L, lastItem.getRemainingDebt());
        assertEquals(LocalDate.of(2027, 11, 1), lastItem.getPaymentDate());
    }

    @Test
    @DisplayName("Некорректные параметры (отрицательная сумма, нулевой срок или ставка) возвращают null")
    void testInvalidParametersReturnNull() {
        assertNull(service.calculateSchedule(-100_000L, 12, 18.5, LocalDate.now()));
        assertNull(service.calculateSchedule(0L, 12, 18.5, LocalDate.now()));
        assertNull(service.calculateSchedule(100_000L, 0, 18.5, LocalDate.now()));
        assertNull(service.calculateSchedule(100_000L, -5, 18.5, LocalDate.now()));
        assertNull(service.calculateSchedule(100_000L, 12, 0.0, LocalDate.now()));
        assertNull(service.calculateSchedule(100_000L, 12, -15.0, LocalDate.now()));
        assertNull(service.calculateSchedule(null, 12, 18.5, LocalDate.now()));
    }

    @Test
    @DisplayName("При передаче null даты используется текущая дата LocalDate.now()")
    void testNullStartDateUsesCurrentDate() {
        PaymentSchedule schedule = service.calculateSchedule(500_000L, 6, 20.0, null);
        assertNotNull(schedule);
        assertEquals(6, schedule.getItems().size());
        assertEquals(LocalDate.now().plusMonths(1), schedule.getItems().get(0).getPaymentDate());
    }

    @Test
    @DisplayName("Одномесячный кредит: один платеж закрывает весь долг и начисленные проценты")
    void testOneMonthCreditSchedule() {
        long amount = 100_000L;
        double rate = 12.0; // 1% в месяц
        PaymentSchedule schedule = service.calculateSchedule(amount, 1, rate, LocalDate.of(2026, 1, 1));

        assertNotNull(schedule);
        assertEquals(1, schedule.getItems().size());
        PaymentScheduleItem item = schedule.getItems().get(0);
        assertEquals(1_000L, item.getInterestPayment()); // 100 000 * 0.01 = 1 000
        assertEquals(100_000L, item.getPrincipalPayment());
        assertEquals(101_000L, item.getTotalPayment());
        assertEquals(0L, item.getRemainingDebt());
    }
}
