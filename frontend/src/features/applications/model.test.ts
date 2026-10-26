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

it('keeps created-time and id ordering after merging a retry result', () => {
  const manualReview = { ...application, status: 'MANUAL_REVIEW' as const };
  const retryResult = { ...application, updatedAt: '2026-10-22T14:30:01Z' };
  const newer = { ...application, id: 43, createdAt: '2026-10-22T15:00:00Z' };
  const sameTime = { ...application, id: 44 };
  expect(mergeApplications([manualReview, newer, sameTime], [retryResult])).toEqual([newer, sameTime, retryResult]);
});
