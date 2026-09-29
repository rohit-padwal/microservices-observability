import { memo, useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Ban, ChevronRight } from 'lucide-react';

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

const OrderRow = memo(function OrderRow({ order, onCancel, onSelect, cancelling }) {
  return (
    <div className="order-row" role="row" aria-rowindex={order.rowIndex}>
      <button className="row-main" type="button" role="cell" onClick={() => onSelect(order.id)} aria-label={`Open order ${order.id}`}>
        <span className="order-identity"><span className="order-id">#{order.id}</span><span className="order-item">{order.itemName}</span></span>
        <span className="order-customer">C-{order.userId}</span>
        <span className="order-amount">{currency.format(Number(order.totalAmount))}</span>
        <span className={`status status-${order.status.toLowerCase()}`}>{order.status.replace('_', ' ')}</span>
        <ChevronRight size={17} className="row-chevron" aria-hidden="true" />
      </button>
      {order.status !== 'CANCELLED' && (
        <button className="row-cancel" type="button" title={`Cancel order ${order.id}`} aria-label={`Cancel order ${order.id}`} disabled={cancelling} onClick={() => onCancel(order.id)}>
          <Ban size={15} aria-hidden="true" />
        </button>
      )}
    </div>
  );
});

export default function OrderList({ orders, onCancel, onSelect, cancellingIds }) {
  const scrollRef = useRef(null);
  const virtualizer = useVirtualizer({
    count: orders.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => 68,
    overscan: 8,
  });

  return (
    <div className="table-frame" role="table" aria-label="Orders">
      <div className="table-head" role="row">
        <span role="columnheader">Order / item</span><span role="columnheader">Customer</span><span role="columnheader">Total</span><span role="columnheader">Status</span><span aria-hidden="true" />
      </div>
      {orders.length === 0 ? (
        <div className="empty-state"><span className="empty-orbit">0</span><h3>No matching orders</h3><p>Try another status or search term.</p></div>
      ) : (
        <div className="virtual-list" ref={scrollRef} role="rowgroup" aria-rowcount={orders.length}>
          <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {virtualizer.getVirtualItems().map((virtualRow) => {
              const order = orders[virtualRow.index];
              return (
                <div key={order.id} style={{ position: 'absolute', top: 0, left: 0, width: '100%', transform: `translateY(${virtualRow.start}px)` }}>
                  <OrderRow
                    order={{ ...order, rowIndex: virtualRow.index + 2 }}
                    onCancel={onCancel}
                    onSelect={onSelect}
                    cancelling={cancellingIds.includes(order.id)}
                  />
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}