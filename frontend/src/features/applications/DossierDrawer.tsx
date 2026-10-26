import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { Button } from '@alfalab/core-components-button';

export function DossierDrawer({ applicationId, onClose, children }: {
  applicationId: number;
  onClose: () => void;
  children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const element = dialog.current!;
    const opener = document.activeElement instanceof HTMLElement && document.activeElement !== document.body
      ? document.activeElement : null;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    closeButton.current?.focus();
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      const target = opener?.isConnected ? opener
        : document.querySelector<HTMLElement>(`button[aria-label="Досье заявки №${applicationId}"]`)
          ?? document.getElementById('registry-title');
      target?.focus();
    };
  }, [applicationId]);

  return <dialog ref={dialog} className="dossier-drawer" aria-modal="true" aria-label={`Досье заявки №${applicationId}`}
    onCancel={(event) => { event.preventDefault(); onClose(); }}>
    <div className="drawer-toolbar">
      <Button ref={closeButton} type="button" view="secondary" size={40} onClick={onClose}>Закрыть досье</Button>
    </div>
    {children}
  </dialog>;
}
