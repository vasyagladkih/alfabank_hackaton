import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { application, deferred } from '../../test/fixtures';
import type { Application } from '../../api/types';
import { pollApplication } from './polling';

describe('serial application polling', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it.each(['APPROVED', 'REJECTED'] as const)('polls at 2 seconds and stops at %s', async (status) => {
    const terminal = { ...application, status, rejectionReason: status === 'REJECTED' ? 'Высокая долговая нагрузка' : null };
    const load = vi.fn().mockResolvedValueOnce(application).mockResolvedValueOnce(terminal);
    const onUpdate = vi.fn();
    const controller = new AbortController();
    const done = pollApplication({ id: 42, signal: controller.signal, load, onUpdate, onError: vi.fn() });
    await vi.advanceTimersByTimeAsync(1999);
    expect(load).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(onUpdate).toHaveBeenCalledWith(application);
    await vi.advanceTimersByTimeAsync(2000);
    await done;
    expect(onUpdate).toHaveBeenLastCalledWith(terminal);
    await vi.advanceTimersByTimeAsync(20000);
    expect(load).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not overlap a slow request and ignores its result after cancellation', async () => {
    const response = deferred<Application>();
    const load = vi.fn(() => response.promise);
    const onUpdate = vi.fn();
    const controller = new AbortController();
    const done = pollApplication({ id: 42, signal: controller.signal, load, onUpdate, onError: vi.fn() });
    await vi.advanceTimersByTimeAsync(20000);
    expect(load).toHaveBeenCalledTimes(1);
    expect(load.mock.calls[0]).toEqual([42, controller.signal]);
    controller.abort();
    response.resolve({ ...application, status: 'APPROVED' });
    await done;
    expect(onUpdate).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('clears a pending timer on unmount/logout', async () => {
    const load = vi.fn();
    const controller = new AbortController();
    const done = pollApplication({ id: 42, signal: controller.signal, load, onUpdate: vi.fn(), onError: vi.fn() });
    controller.abort();
    await done;
    await vi.advanceTimersByTimeAsync(10000);
    expect(load).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('shows a transient error, retries and clears the error on success', async () => {
    const error = new ApiError(503, 'Временно недоступно');
    const load = vi.fn().mockRejectedValueOnce(error).mockResolvedValueOnce({ ...application, status: 'APPROVED' });
    const onError = vi.fn();
    const done = pollApplication({ id: 42, signal: new AbortController().signal, load, onUpdate: vi.fn(), onError });
    await vi.advanceTimersByTimeAsync(2000);
    expect(onError).toHaveBeenCalledWith(error, true);
    await vi.advanceTimersByTimeAsync(2000);
    await done;
    expect(onError).toHaveBeenLastCalledWith(null, false);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it.each([400, 401, 403, 404])('stops instead of retrying HTTP %s', async (status) => {
    const error = new ApiError(status, 'Запрос отклонён');
    const load = vi.fn().mockRejectedValue(error);
    const onError = vi.fn();
    const done = pollApplication({ id: 42, signal: new AbortController().signal, load, onUpdate: vi.fn(), onError });
    await vi.advanceTimersByTimeAsync(2000);
    await done;
    await vi.advanceTimersByTimeAsync(10000);
    expect(load).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenLastCalledWith(error, false);
  });

  it('pauses after three consecutive network errors for explicit retry', async () => {
    const error = new ApiError(0, 'Нет соединения');
    const load = vi.fn().mockRejectedValue(error);
    const onError = vi.fn();
    const done = pollApplication({ id: 42, signal: new AbortController().signal, load, onUpdate: vi.fn(), onError });
    await vi.advanceTimersByTimeAsync(6000);
    await done;
    expect(load).toHaveBeenCalledTimes(3);
    expect(onError).toHaveBeenLastCalledWith(error, false);
    expect(vi.getTimerCount()).toBe(0);
  });
});
