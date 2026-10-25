package ru.alfahackaton.service;

import org.springframework.stereotype.Service;
import ru.alfahackaton.dto.PaymentSchedule;
import ru.alfahackaton.dto.PaymentScheduleItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис детерминированного расчета аннуитетного графика кредитных платежей.
 */
@Service
public class PaymentScheduleService {

    /**
     * Формирование помесячного графика аннуитетных платежей.
     *
     * @param approvedAmount одобренная сумма кредита в рублях
     * @param termMonths     срок кредитования в месяцах
     * @param approvedRate   годовая процентная ставка в % (например, 18.5)
     * @param startDate      дата выдачи / начала отсчета
     * @return PaymentSchedule со сводными суммами и помесячной разбивкой
     */
    public PaymentSchedule calculateSchedule(
            Long approvedAmount,
            Integer termMonths,
            Double approvedRate,
            LocalDate startDate
    ) {
        if (approvedAmount == null || approvedAmount <= 0
                || termMonths == null || termMonths <= 0
                || approvedRate == null || approvedRate <= 0) {
            return null;
        }

        LocalDate baseDate = startDate != null ? startDate : LocalDate.now();
        double monthlyRate = (approvedRate / 100.0) / 12.0;
        int periods = termMonths;

        double factor = Math.pow(1.0 + monthlyRate, periods);
        double annuityFactor = (monthlyRate * factor) / (factor - 1.0);
        long fixedMonthlyPayment = Math.round(approvedAmount * annuityFactor);

        long currentDebt = approvedAmount;
        long totalInterestAccrued = 0L;
        List<PaymentScheduleItem> items = new ArrayList<>(periods);

        for (int month = 1; month <= periods; month++) {
            LocalDate paymentDate = baseDate.plusMonths(month);
            long interestPayment = Math.round(currentDebt * monthlyRate);
            long principalPayment;
            long monthTotal;

            if (month == periods) {
                // В последний месяц остаток долга закрывается полностью
                principalPayment = currentDebt;
                monthTotal = principalPayment + interestPayment;
                currentDebt = 0L;
            } else {
                principalPayment = fixedMonthlyPayment - interestPayment;
                monthTotal = fixedMonthlyPayment;
                currentDebt = Math.max(0L, currentDebt - principalPayment);
            }

            totalInterestAccrued += interestPayment;

            PaymentScheduleItem item = new PaymentScheduleItem();
            item.setMonthNumber(month);
            item.setPaymentDate(paymentDate);
            item.setTotalPayment(monthTotal);
            item.setPrincipalPayment(principalPayment);
            item.setInterestPayment(interestPayment);
            item.setRemainingDebt(currentDebt);

            items.add(item);
        }

        PaymentSchedule schedule = new PaymentSchedule();
        schedule.setMonthlyPayment(fixedMonthlyPayment);
        schedule.setTotalInterest(totalInterestAccrued);
        schedule.setTotalPayout(approvedAmount + totalInterestAccrued);
        schedule.setItems(items);

        return schedule;
    }
}
