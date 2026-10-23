import { expect, it } from 'vitest';
import { mergeApplications } from './model';
import { application } from '../../test/fixtures';

it('does not let a stale history response overwrite a decision or a new submission', () => {
  const decision = { ...application, status: 'APPROVED' as const, version: 1 };
  const newer = { ...application, id: 43, createdAt: '2026-10-22T15:00:00Z' };
  expect(mergeApplications([decision, newer], [application])).toEqual([newer, decision]);
});

it('keeps a newer decision when a delayed manual-review response has a later timestamp', () => {
  const decision = { ...application, status: 'APPROVED' as const, version: 3 };
  const delayed = { ...application, status: 'MANUAL_REVIEW' as const, version: 1, updatedAt: '2026-10-23T15:00:00Z' };
  expect(mergeApplications([decision], [delayed])).toEqual([decision]);
});

it('accepts a higher-version offer response even when the server timestamp is equal', () => {
  const manual = { ...application, status: 'MANUAL_REVIEW' as const, version: 1 };
  const offer = { ...application, status: 'OFFER_PROPOSED' as const, version: 2 };
  expect(mergeApplications([offer], [manual])).toEqual([offer]);
  expect(mergeApplications([manual], [offer])).toEqual([offer]);
});

it('does not roll back a terminal decision on an inconsistent higher-version pending response', () => {
  const decision = { ...application, status: 'REJECTED' as const, version: 2 };
  const pending = { ...application, status: 'OFFER_PROPOSED' as const, version: 3 };
  expect(mergeApplications([decision], [pending])).toEqual([decision]);
});
