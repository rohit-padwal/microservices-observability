import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Activity, ArrowDownRight, ArrowUpRight, RefreshCw, Search, X } from 'lucide-react';
import { cancelOrder, createOrder, dismissSaveError, fetchOrders, fetchOrderStatistics } from '../features/orders/ordersSlice.js';
import OrderForm from '../components/OrderForm.jsx';
import OrderList from '../components/OrderList.jsx';

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

/** Operations page combining global metrics, a filtered Redux-backed server page, and create/detail workflows. */
export default function OrderDashboard() {
  const dispatch = useDispatch();
  const { items, statistics, page, size, totalElements, totalPages, status, error, saving, saveError, cancellingIds } = useSelector((state) => state.orders);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [pageIndex, setPageIndex] = useState(0);
  const [selectedId, setSelectedId] = useState(null);
  const searchRef = useRef(null);
  const deferredSearch = useDeferredValue(debouncedSearch);
  // A stable query object keeps the effect tied to actual filters, and the abort prevents stale responses winning races.
  const currentQuery = useMemo(() => ({
    page: pageIndex,
    size: 20,
    sort: 'createdAt',
    direction: 'DESC',
    status: statusFilter === 'ALL' ? undefined : statusFilter,
    itemName: deferredSearch.trim() || undefined,
  }), [pageIndex, statusFilter, deferredSearch]);

  useEffect(() => {
    const request = dispatch(fetchOrders(currentQuery));
    searchRef.current?.focus();
    return () => request.abort();
  }, [dispatch, currentQuery]);

  useEffect(() => {
    // Keep global totals independent of the current filter/page so summary values always describe all orders.
    const request = dispatch(fetchOrderStatistics());
    return () => request.abort();
  }, [dispatch]);

  useEffect(() => {
    // Debounce server search while deferred rendering keeps typing responsive on large result sets.
    const timeout = window.setTimeout(() => setDebouncedSearch(search), 250);
    return () => window.clearTimeout(timeout);
  }, [search]);

  const visibleOrders = useMemo(() => {
    const term = deferredSearch.trim().toLowerCase();
    return [...items].filter((order) => !term || order.itemName.toLowerCase().includes(term));
  }, [items, deferredSearch]);

  const paidTotal = Number(statistics?.paidVolume ?? 0);

  const refreshOrders = useCallback(() => dispatch(fetchOrders(currentQuery)), [dispatch, currentQuery]);

  const handleCancel = useCallback(async (id) => {
    await dispatch(cancelOrder(id)).unwrap();
    dispatch(fetchOrders(currentQuery));
    dispatch(fetchOrderStatistics());
  }, [dispatch, currentQuery]);

  const handleCreate = useCallback(async (order) => {
    await dispatch(createOrder(order)).unwrap();
    if (pageIndex === 0) refreshOrders();
    else setPageIndex(0);
    dispatch(fetchOrderStatistics());
  }, [dispatch, pageIndex, refreshOrders]);

  const selectedOrder = items.find((order) => order.id === selectedId);
  const apiLabel = status === 'succeeded' ? 'API synced' : status === 'loading' ? 'Syncing' : status === 'failed' ? 'API unavailable' : 'API idle';

  return (
    <div className="dashboard-page">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Commerce / live operations</p>
          <h1>Order desk<span className="heading-period">.</span></h1>
          <p className="page-deck">A live view of orders moving through the Spring Boot payment flow.</p>
        </div>
        <div className={`api-indicator api-${status}`}><Activity size={15} aria-hidden="true" /><span>{apiLabel}</span></div>
      </header>

      <section className="metrics-strip" aria-label="Order summary">
      <div className="metric"><span className="metric-label">Total orders</span><strong>{statistics?.totalOrders ?? '—'}</strong><span className="metric-note">all time / service response</span></div>
      <div className="metric"><span className="metric-label">Paid orders</span><strong>{statistics?.paidOrders ?? '—'}</strong><span className="metric-note"><ArrowUpRight size={13} /> payment completed</span></div>
        <div className="metric"><span className="metric-label">Paid volume</span><strong>{money.format(paidTotal)}</strong><span className="metric-note"><ArrowDownRight size={13} /> based on current results</span></div>
      </section>

      <div className="workbench">
        <OrderForm onCreate={handleCreate} saving={saving} />
        <section className="orders-panel" aria-labelledby="orders-title">
          <div className="section-heading orders-heading">
            <div><p className="eyebrow">Service response</p><h2 id="orders-title">Recent orders <span className="count-badge">{totalElements}</span></h2></div>
            <button className="icon-button" type="button" title="Refresh orders" aria-label="Refresh orders" disabled={status === 'loading'} onClick={refreshOrders}><RefreshCw size={16} className={status === 'loading' ? 'spin' : ''} /></button>
          </div>
          <div className="list-controls">
            <label className="search-control"><Search size={16} aria-hidden="true" /><input ref={searchRef} value={search} onChange={(event) => { setSearch(event.target.value); setPageIndex(0); }} placeholder="Search item name…" aria-label="Search item name" /><kbd>/</kbd></label>
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPageIndex(0); }} aria-label="Filter by status">
              <option value="ALL">All statuses</option><option value="PAID">Paid</option><option value="CREATED">Created</option><option value="PAYMENT_FAILED">Payment failed</option><option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          {error && <div className="notice notice-error" role="alert"><span>{error}</span><button type="button" className="text-button" onClick={refreshOrders}>Retry</button></div>}
          {saveError && <div className="notice notice-error" role="alert"><span>{saveError}</span><button type="button" className="icon-button compact" aria-label="Dismiss error" onClick={() => dispatch(dismissSaveError())}><X size={15} /></button></div>}
          {status === 'loading' && items.length === 0 ? <div className="loading-state"><span className="loader" />Loading orders from Order Service</div> : (
            <OrderList orders={visibleOrders} onCancel={handleCancel} onSelect={setSelectedId} cancellingIds={cancellingIds} />
          )}
          <div className="pagination-bar">
            <span>{totalElements} matching orders · page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
            <div className="pagination-actions"><button className="text-button" type="button" disabled={pageIndex <= 0 || status === 'loading'} onClick={() => setPageIndex((current) => Math.max(0, current - 1))}>Previous</button><button className="text-button" type="button" disabled={pageIndex + 1 >= totalPages || status === 'loading'} onClick={() => setPageIndex((current) => current + 1)}>Next</button></div>
          </div>
          <div className="list-footer"><span>Sorted by newest order · {size} per page</span><span>Virtual rows · {items.length} loaded</span></div>
        </section>
      </div>

      {selectedOrder && <div className="detail-backdrop" role="presentation" onClick={() => setSelectedId(null)}>
        <section className="detail-drawer" role="dialog" aria-modal="true" aria-labelledby="detail-title" onClick={(event) => event.stopPropagation()}>
          <button className="icon-button detail-close" type="button" aria-label="Close order details" onClick={() => setSelectedId(null)}><X size={18} /></button>
          <p className="eyebrow">Order record / #{selectedOrder.id}</p><h2 id="detail-title">{selectedOrder.itemName}</h2>
          <dl className="detail-list"><div><dt>Customer</dt><dd>{selectedOrder.userId}</dd></div><div><dt>Quantity</dt><dd>{selectedOrder.quantity}</dd></div><div><dt>Total</dt><dd>{new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(selectedOrder.totalAmount))}</dd></div><div><dt>Status</dt><dd><span className={`status status-${selectedOrder.status.toLowerCase()}`}>{selectedOrder.status.replace('_', ' ')}</span></dd></div><div><dt>Created at</dt><dd>{selectedOrder.createdAt ? new Date(selectedOrder.createdAt).toLocaleString() : 'Not returned'}</dd></div></dl>
          <p className="detail-caption">The row child sends this selected ID back to its parent; details are derived from the Redux store.</p>
        </section>
      </div>}
    </div>
  );
}