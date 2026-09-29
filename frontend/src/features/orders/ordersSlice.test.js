import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { configureStore } from '@reduxjs/toolkit';
import ordersReducer, { fetchOrders } from './ordersSlice.js';

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
});

test('fetchOrders sends server filters and stores page metadata', async () => {
  let requestedUrl;
  globalThis.fetch = async (url) => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({
      content: [{ id: 8, itemName: 'Field recorder', status: 'PAID' }],
      number: 1,
      size: 20,
      totalPages: 3,
      totalElements: 41,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  const store = configureStore({ reducer: { orders: ordersReducer } });

  await store.dispatch(fetchOrders({ page: 1, size: 20, itemName: 'recorder', status: 'PAID' }));

  assert.match(requestedUrl, /^\/api\/orders\?/);
  assert.match(requestedUrl, /page=1/);
  assert.match(requestedUrl, /itemName=recorder/);
  assert.equal(store.getState().orders.items[0].id, 8);
  assert.equal(store.getState().orders.totalElements, 41);
  assert.equal(store.getState().orders.totalPages, 3);
  assert.equal(store.getState().orders.status, 'succeeded');
});

test('fetchOrders exposes server errors in Redux state', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Order Service unavailable' }), {
    status: 503,
    headers: { 'Content-Type': 'application/json' },
  });
  const store = configureStore({ reducer: { orders: ordersReducer } });

  await store.dispatch(fetchOrders({ page: 0, size: 20 }));

  assert.equal(store.getState().orders.status, 'failed');
  assert.equal(store.getState().orders.error, 'Order Service unavailable');
});