import { Button } from '@alfalab/core-components-button';
import { Plate } from '@alfalab/core-components-plate';
import { Spinner } from '@alfalab/core-components-spinner';
import { Typography } from '@alfalab/core-components-typography';
import { DocumentMIcon } from '@alfalab/icons-glyph/DocumentMIcon';

export function Loading({ children = 'Загружаем заявки…' }: { children?: string }) {
  return <div className="loading-state" role="status">
    <span aria-hidden="true"><Spinner visible preset={24} /></span>
    <Typography.Text>{children}</Typography.Text>
  </div>;
}

export function ErrorNotice({ title = 'Не удалось выполнить запрос', message, retry }: { title?: string; message: string; retry?: () => void }) {
  return <div className="notice">
    <Plate view="negative" title={title} titleView="bold" rounded limitContentWidth={false}
      buttons={retry ? <Button size={40} view="secondary" onClick={retry}>Повторить</Button> : undefined}>
      {message}
    </Plate>
  </div>;
}

export function EmptyState({ title, children }: { title: string; children: string }) {
  return <div className="empty-state">
    <span className="icon-tile" aria-hidden="true"><DocumentMIcon /></span>
    <Typography.Title tag="h3" view="xsmall" font="system" defaultMargins={false}>{title}</Typography.Title>
    <Typography.Text tag="p" view="primary-small" color="secondary" defaultMargins={false}>{children}</Typography.Text>
  </div>;
}
