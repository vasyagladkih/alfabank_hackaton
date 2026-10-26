import { Table } from '@alfalab/core-components-table';
import { Status } from '@alfalab/core-components-status';
import { Button } from '@alfalab/core-components-button';
import type { Application, ApplicationStatus } from '../../api/types';
import { formatDate, formatMoney, PURPOSE_LABELS, STATUS_LABELS } from './model';

const colors = { PROCESSING: 'blue', MANUAL_REVIEW: 'blue', APPROVED: 'green', SIGNED: 'green', REJECTED: 'red' } as const;

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  return <Status color={colors[status]} view="muted-alt" className="readable-status" uppercase={false} size={24}>{STATUS_LABELS[status]}</Status>;
}

export function ApplicationsTable({ applications, employee = false, onSelect }: { applications: Application[]; employee?: boolean; onSelect?: (application: Application) => void }) {
  const label = employee ? 'Реестр кредитных заявок' : 'История заявок';
  return <div className="table-scroll" role="region" aria-label={label} tabIndex={0}>
    <Table className="applications-table" wrapper={false} aria-label={label}>
      {/* Core Table inspects every head child's props; omit absent cells entirely. */}
      <Table.THead>{[
        <Table.THeadCell key="id" scope="col">ID заявки</Table.THeadCell>,
        ...(employee ? [<Table.THeadCell key="borrower" scope="col">Заёмщик · ИНН</Table.THeadCell>] : []),
        <Table.THeadCell key="amount" scope="col" textAlign="right">Сумма</Table.THeadCell>,
        <Table.THeadCell key="term" scope="col">Срок</Table.THeadCell>,
        <Table.THeadCell key="purpose" scope="col">Цель</Table.THeadCell>,
        <Table.THeadCell key="status" scope="col">Статус</Table.THeadCell>,
        <Table.THeadCell key="date" scope="col">Дата создания</Table.THeadCell>,
      ]}</Table.THead>
      <Table.TBody>
        {applications.map((application) => <Table.TRow key={application.id}>{[
          <Table.TCell key="id">{onSelect ? <Button type="button" view="secondary" size={40} className="application-id"
            aria-label={`${employee ? 'Досье' : 'Условия'} заявки №${application.id}`} onClick={() => onSelect(application)}>№{application.id}</Button>
            : <span className="application-id">№{application.id}</span>}</Table.TCell>,
          ...(employee ? [<Table.TCell key="borrower"><span className="borrower-name">{application.companyName || 'Компания не указана'}</span><span className="table-secondary">{application.inn || 'ИНН не указан'}</span></Table.TCell>] : []),
          <Table.TCell key="amount" className="money-cell">{formatMoney(application.amount)}</Table.TCell>,
          <Table.TCell key="term" className="nowrap">{application.termMonths} мес.</Table.TCell>,
          <Table.TCell key="purpose">{PURPOSE_LABELS[application.purpose]}</Table.TCell>,
          <Table.TCell key="status">
            <ApplicationStatusBadge status={application.status} />
            {application.status === 'REJECTED' && <span className="rejection-detail">{application.rejectionReason || 'Причина отказа не указана.'}</span>}
          </Table.TCell>,
          <Table.TCell key="date" className="date-cell"><time dateTime={application.createdAt}>{formatDate(application.createdAt)}</time></Table.TCell>,
        ]}</Table.TRow>)}
      </Table.TBody>
    </Table>
  </div>;
}
