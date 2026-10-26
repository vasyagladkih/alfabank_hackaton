import { expect, it } from 'vitest';
import type { ApplicationStatus } from '../../api/types';
import { mergeApplications } from './model';
import { application } from '../../test/fixtures';

it('does not let a stale history response overwrite a decision or a new submission', () => {
  const decision = { ...application, status: 'APPROVED' as const };
  const newer = { ...application, id: 43, createdAt: '2026-10-22T15:00:00Z' };
  expect(mergeApplications([decision, newer], [application])).toEqual([newer, decision]);
});

it.each<{
  previousStatus: ApplicationStatus;
  incomingStatus: ApplicationStatus;
  incomingTime: string;
  accept: boolean;
}>([
  { previousStatus: 'PROCESSING', incomingStatus: 'APPROVED', incomingTime: '2026-10-22T14:29:59Z', accept: false },
  { previousStatus: 'PROCESSING', incomingStatus: 'APPROVED', incomingTime: '2026-10-22T14:30:00Z', accept: true },
  { previousStatus: 'PROCESSING', incomingStatus: 'REJECTED', incomingTime: '2026-10-22T14:30:00Z', accept: true },
  { previousStatus: 'PROCESSING', incomingStatus: 'MANUAL_REVIEW', incomingTime: '2026-10-22T14:30:00Z', accept: true },
  { previousStatus: 'MANUAL_REVIEW', incomingStatus: 'PROCESSING', incomingTime: '2026-10-22T14:29:59Z', accept: false },
  { previousStatus: 'MANUAL_REVIEW', incomingStatus: 'PROCESSING', incomingTime: '2026-10-22T14:30:00Z', accept: false },
  { previousStatus: 'MANUAL_REVIEW', incomingStatus: 'PROCESSING', incomingTime: '2026-10-22T14:30:01Z', accept: true },
  { previousStatus: 'MANUAL_REVIEW', incomingStatus: 'APPROVED', incomingTime: '2026-10-22T14:30:01Z', accept: true },
  { previousStatus: 'MANUAL_REVIEW', incomingStatus: 'REJECTED', incomingTime: '2026-10-22T14:30:01Z', accept: true },
  { previousStatus: 'APPROVED', incomingStatus: 'PROCESSING', incomingTime: '2026-10-22T14:30:01Z', accept: false },
  { previousStatus: 'REJECTED', incomingStatus: 'PROCESSING', incomingTime: '2026-10-22T14:30:01Z', accept: false },
  { previousStatus: 'APPROVED', incomingStatus: 'SIGNED', incomingTime: '2026-10-22T14:29:59Z', accept: false },
  { previousStatus: 'APPROVED', incomingStatus: 'SIGNED', incomingTime: '2026-10-22T14:30:00Z', accept: true },
  { previousStatus: 'APPROVED', incomingStatus: 'SIGNED', incomingTime: '2026-10-22T14:30:01Z', accept: true },
])('merges $previousStatus → $incomingStatus at $incomingTime (accept: $accept)', ({ previousStatus, incomingStatus, incomingTime, accept }) => {
  const previous = { ...application, status: previousStatus };
  const incoming = { ...application, status: incomingStatus, updatedAt: incomingTime };
  expect(mergeApplications([previous], [incoming])).toEqual([accept ? incoming : previous]);
});

it.each(['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'REJECTED'] as const)('never replaces SIGNED with %s, even at the same millisecond', (status) => {
  const signed = { ...application, status: 'SIGNED' as const };
  for (const updatedAt of [application.updatedAt, '2026-10-22T14:30:01Z']) {
    expect(mergeApplications([signed], [{ ...application, status, updatedAt }])).toEqual([signed]);
  }
});

it.each(['PROCESSING', 'MANUAL_REVIEW', 'REJECTED'] as const)('does not reopen APPROVED as %s after a late response', (status) => {
  const approved = { ...application, status: 'APPROVED' as const };
  for (const updatedAt of [undefined, application.updatedAt, '2026-10-22T14:30:01Z']) {
    expect(mergeApplications([approved], [{ ...application, status, updatedAt }])).toEqual([approved]);
  }
});

it.each(['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED'] as const)('does not reopen REJECTED as %s after a late response', (status) => {
  const rejected = { ...application, status: 'REJECTED' as const };
  for (const updatedAt of [undefined, application.updatedAt, '2026-10-22T14:30:01Z']) {
    expect(mergeApplications([rejected], [{ ...application, status, updatedAt }])).toEqual([rejected]);
  }
});

it.each(['APPROVED', 'REJECTED', 'SIGNED'] as const)('accepts a same-state refresh for %s', (status) => {
  const previous = { ...application, status };
  const refreshed = { ...previous, companyName: 'Обновлённое название' };
  expect(mergeApplications([previous], [refreshed])).toEqual([refreshed]);
});

it.each([
  ['2026-10-22T14:30:01Z', undefined],
  [undefined, '2026-10-22T14:30:01Z'],
  [undefined, undefined],
])('accepts forward signing with optional update times %s → %s', (previousTime, incomingTime) => {
  const approved = { ...application, status: 'APPROVED' as const, updatedAt: previousTime };
  const signed = { ...application, status: 'SIGNED' as const, updatedAt: incomingTime };
  expect(mergeApplications([approved], [signed])).toEqual([signed]);
  expect(mergeApplications([signed], [approved])).toEqual([signed]);
});

it.each(['APPROVED', 'REJECTED', 'MANUAL_REVIEW'] as const)('accepts a forward PROCESSING → %s decision without an update time', (status) => {
  const processing = { ...application, updatedAt: '2026-10-22T14:30:01Z' };
  const decision = { ...application, status, updatedAt: undefined };
  expect(mergeApplications([processing], [decision])).toEqual([decision]);
});

it.each([
  ['2026-10-22T14:30:00Z', undefined],
  [undefined, '2026-10-22T14:30:01Z'],
  [undefined, undefined],
])('does not infer a retry from missing update times %s → %s', (previousTime, incomingTime) => {
  const manualReview = { ...application, status: 'MANUAL_REVIEW' as const, updatedAt: previousTime };
  const processing = { ...application, updatedAt: incomingTime };
  expect(mergeApplications([manualReview], [processing])).toEqual([manualReview]);
});

it.each([
  ['2026-10-22T14:30:00.123456Z', '2026-10-22T14:30:00.123455Z'],
  ['2026-10-22T17:30:00.123456+03:00', '2026-10-22T14:30:00.123455Z'],
  ['2026-10-22T14:30:00.123456789Z', '2026-10-22T09:30:00.123456788-05:00'],
])('ignores an older same-state response at full timestamp precision %s → %s', (previousTime, incomingTime) => {
  const previous = { ...application, status: 'APPROVED' as const, updatedAt: previousTime };
  const stale = { ...previous, updatedAt: incomingTime, approvedRate: 99 };
  expect(mergeApplications([previous], [stale])).toEqual([previous]);
});

it.each([
  ['2026-10-22T14:30:00.123456Z', '2026-10-22T14:30:00.123457Z'],
  ['2026-10-22T17:30:00.123456+03:00', '2026-10-22T14:30:00.123457Z'],
  ['2026-10-22T14:30:00.123456788Z', '2026-10-22T09:30:00.123456789-05:00'],
  ['2026-10-22T14:30:00.123999Z', '2026-10-22T14:30:00.124001Z'],
])('accepts a provably newer retry at full timestamp precision %s → %s', (previousTime, incomingTime) => {
  const manualReview = { ...application, status: 'MANUAL_REVIEW' as const, updatedAt: previousTime };
  const processing = { ...application, updatedAt: incomingTime };
  expect(mergeApplications([manualReview], [processing])).toEqual([processing]);
});

it.each([
  ['2026-10-22T14:30:00.123400Z', '2026-10-22T14:30:00.1234Z'],
  ['2026-10-22T17:30:00.123400+03:00', '2026-10-22T14:30:00.1234Z'],
  ['2026-10-22T14:30:00Z', '2026-10-22T09:30:00.000000000-05:00'],
])('does not infer a retry from equal instants with different representations %s → %s', (previousTime, incomingTime) => {
  const manualReview = { ...application, status: 'MANUAL_REVIEW' as const, updatedAt: previousTime };
  const processing = { ...application, updatedAt: incomingTime };
  expect(mergeApplications([manualReview], [processing])).toEqual([manualReview]);
});

it('sorts creation timestamps at full precision before using the ID tiebreaker', () => {
  const older = { ...application, id: 43, createdAt: '2026-10-22T17:30:00.123456+03:00' };
  const newer = { ...application, id: 42, createdAt: '2026-10-22T14:30:00.123457Z' };
  expect(mergeApplications([older, newer])).toEqual([newer, older]);
});

it('keeps created-time and id ordering after merging a retry result', () => {
  const manualReview = { ...application, status: 'MANUAL_REVIEW' as const };
  const retryResult = { ...application, updatedAt: '2026-10-22T14:30:01Z' };
  const newer = { ...application, id: 43, createdAt: '2026-10-22T15:00:00Z' };
  const sameTime = { ...application, id: 44 };
  expect(mergeApplications([manualReview, newer, sameTime], [retryResult])).toEqual([newer, sameTime, retryResult]);
});
