import { Table } from '@alfalab/core-components-table';
import { Typography } from '@alfalab/core-components-typography';
import type { PaymentSchedule } from '../../api/types';
import { formatMoney } from './model';

export function PaymentScheduleView({ schedule }: { schedule: PaymentSchedule }) {
  return <div className="payment-schedule">
    <Typography.Title tag="h3" view="xsmall" font="system" defaultMargins={false}>График платежей</Typography.Title>
    <dl className="details-grid">
      <div><dt>Ежемесячный платёж</dt><dd>{formatMoney(schedule.monthlyPayment)}</dd></div>
      <div><dt>Сумма процентов</dt><dd>{formatMoney(schedule.totalInterest)}</dd></div>
      <div><dt>Всего к выплате</dt><dd>{formatMoney(schedule.totalPayout)}</dd></div>
    </dl>
    <div className="table-scroll" role="region" aria-label="График платежей" tabIndex={0}>
      <Table className="applications-table" wrapper={false} aria-label="График платежей">
        <Table.THead>{[
          <Table.THeadCell key="month" scope="col">Месяц</Table.THeadCell>,
          <Table.THeadCell key="date" scope="col">Дата платежа</Table.THeadCell>,
          <Table.THeadCell key="payment" scope="col" textAlign="right">Платёж</Table.THeadCell>,
          <Table.THeadCell key="principal" scope="col" textAlign="right">Основной долг</Table.THeadCell>,
          <Table.THeadCell key="interest" scope="col" textAlign="right">Проценты</Table.THeadCell>,
          <Table.THeadCell key="remaining" scope="col" textAlign="right">Остаток долга</Table.THeadCell>,
        ]}</Table.THead>
        <Table.TBody>
          {schedule.items.map((item) => <Table.TRow key={item.monthNumber}>{[
            <Table.TCell key="month">{item.monthNumber}</Table.TCell>,
            <Table.TCell key="date" className="date-cell"><time dateTime={item.paymentDate}>{item.paymentDate.split('-').reverse().join('.')}</time></Table.TCell>,
            <Table.TCell key="payment" className="money-cell">{formatMoney(item.totalPayment)}</Table.TCell>,
            <Table.TCell key="principal" className="money-cell">{formatMoney(item.principalPayment)}</Table.TCell>,
            <Table.TCell key="interest" className="money-cell">{formatMoney(item.interestPayment)}</Table.TCell>,
            <Table.TCell key="remaining" className="money-cell">{formatMoney(item.remainingDebt)}</Table.TCell>,
          ]}</Table.TRow>)}
        </Table.TBody>
      </Table>
    </div>
  </div>;
}
