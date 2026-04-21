import { Fragment } from 'react';

interface DescriptionItem {
  label: string;
  value: React.ReactNode;
}

interface DescriptionListProps {
  items: DescriptionItem[];
}

export function DescriptionList({ items }: DescriptionListProps) {
  return (
    <dl style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '0' }}>
      {items.map((item, index) => (
        <Fragment key={item.label}>
          <dt
            style={{
              color: '#6b7280',
              fontWeight: 500,
              fontSize: '0.875rem',
              padding: '12px 0',
              borderBottom: index < items.length - 1 ? '1px solid #f3f4f6' : 'none',
            }}
          >
            {item.label}
          </dt>
          <dd
            style={{
              color: '#111827',
              fontSize: '0.875rem',
              padding: '12px 0',
              margin: 0,
              borderBottom: index < items.length - 1 ? '1px solid #f3f4f6' : 'none',
            }}
          >
            {item.value}
          </dd>
        </Fragment>
      ))}
    </dl>
  );
}
