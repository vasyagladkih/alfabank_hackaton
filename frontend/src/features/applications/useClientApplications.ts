import { useCallback, useEffect, useRef, useState } from 'react';
import type { ApiClient } from '../../api/client';
import { errorMessage, isAbortError } from '../../api/client';
import type { Application } from '../../api/types';
import { isAwaitingResolution, mergeApplications } from './model';
import { pollApplication } from './polling';

export interface PollIssue { message: string; retrying: boolean; }

export function useClientApplications(api: ApiClient) {
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pollIssues, setPollIssues] = useState<Record<number, PollIssue>>({});
  const [retryVersion, setRetryVersion] = useState(0);
  const historyRequest = useRef<AbortController | null>(null);
  // Keep completed/paused loops registered until their ID reaches a final state or
  // the user explicitly retries. Other applications must not restart them.
  const polls = useRef(new Map<number, AbortController>());
  const stopAllPolls = useCallback(() => {
    for (const controller of polls.current.values()) controller.abort();
    polls.current.clear();
  }, []);
  useEffect(() => () => stopAllPolls(), [api, stopAllPolls]);

  const update = useCallback((application: Application) => {
    setApplications((current) => mergeApplications(current, [application]));
  }, []);

  const refresh = useCallback(() => {
    historyRequest.current?.abort();
    const controller = new AbortController();
    historyRequest.current = controller;
    setLoading(true);
    setError(null);
    void api.getClientApplications(controller.signal).then((items) => {
      if (!controller.signal.aborted) setApplications((current) => mergeApplications(current, items));
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted && !isAbortError(cause)) setError(errorMessage(cause));
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
  }, [api]);

  useEffect(() => { refresh(); return () => historyRequest.current?.abort(); }, [refresh]);

  const pendingIds = applications.filter(({ status }) => isAwaitingResolution(status)).map(({ id }) => id).sort((a, b) => a - b).join(',');
  useEffect(() => {
    const pending = new Set(pendingIds.split(',').filter(Boolean).map(Number));
    setPollIssues((current) => {
      const remaining = Object.entries(current).filter(([id]) => pending.has(Number(id)));
      return remaining.length === Object.keys(current).length ? current : Object.fromEntries(remaining);
    });
    for (const [id, controller] of polls.current) {
      if (!pending.has(id)) { controller.abort(); polls.current.delete(id); }
    }
    for (const id of pending) {
      if (polls.current.has(id)) continue;
      const controller = new AbortController();
      polls.current.set(id, controller);
      void pollApplication({
        id, signal: controller.signal, load: api.getApplication, onUpdate: update,
        onError: (cause, retrying) => {
          if (controller.signal.aborted) return;
          setPollIssues((current) => {
            const next = { ...current };
            if (cause === null) delete next[id];
            else next[id] = { message: errorMessage(cause), retrying };
            return next;
          });
        },
      });
    }
  }, [pendingIds, retryVersion, api, update]);

  const retryPolling = (id: number) => {
    polls.current.get(id)?.abort();
    polls.current.delete(id);
    setPollIssues((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
    setRetryVersion((version) => version + 1);
  };

  return { applications, loading, error, pollIssues, refresh, update, retryPolling };
}
