import type { Application } from '../../api/types';
import { ApiError, isAbortError } from '../../api/client';
import { isTerminal } from './model';

export interface PollOptions {
  id: number;
  signal: AbortSignal;
  load: (id: number, signal: AbortSignal) => Promise<Application>;
  onUpdate: (application: Application) => void;
  onError: (error: unknown | null, retrying: boolean) => void;
}
function waitForNextPoll(signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const finish = () => {
      clearTimeout(timer);
      signal.removeEventListener('abort', finish);
      resolve();
    };
    const timer = setTimeout(finish, 2000);
    signal.addEventListener('abort', finish, { once: true });
    if (signal.aborted) finish();
  });
}

export async function pollApplication({ id, signal, load, onUpdate, onError }: PollOptions): Promise<void> {
  let consecutiveFailures = 0;
  while (!signal.aborted) {
    await waitForNextPoll(signal);
    if (signal.aborted) return;
    try {
      const application = await load(id, signal);
      if (signal.aborted) return;
      consecutiveFailures = 0;
      onError(null, false);
      onUpdate(application);
      if (isTerminal(application.status)) return;
    } catch (error) {
      if (signal.aborted || isAbortError(error)) return;
      consecutiveFailures += 1;
      const permanent = error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 429;
      const retrying = !permanent && consecutiveFailures < 3;
      onError(error, retrying);
      if (!retrying) return;
    }
  }
}
