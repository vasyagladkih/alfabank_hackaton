import { expect, it } from 'vitest';
import { mergeApplications } from './model';
import { application } from '../../test/fixtures';

it('does not let a stale history response overwrite a decision or a new submission', () => {
  const decision = { ...application, status: 'APPROVED' as const };
  const newer = { ...application, id: 43, createdAt: '2026-10-22T15:00:00Z' };
  expect(mergeApplications([decision, newer], [application])).toEqual([newer, decision]);
});
