import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// jsdom has no layout engine or matchMedia. Components remain real; responsive
// layout and media-query changes are checked in Chromium, not by this shim.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string): MediaQueryList => {
    const target = new EventTarget();
    const widths = [...query.matchAll(/(min|max)-width:\s*(\d+)px/g)];
    return Object.assign(target, {
      matches: widths.every(([, bound, width]) => bound === 'min'
        ? window.innerWidth >= Number(width) : window.innerWidth <= Number(width)),
      media: query,
      onchange: null,
      addListener: (listener: EventListener) => target.addEventListener('change', listener),
      removeListener: (listener: EventListener) => target.removeEventListener('change', listener),
    }) as MediaQueryList;
  },
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});
