import { createAsyncThunk, createSelector, createSlice } from '@reduxjs/toolkit';
import { getAccessToken } from '../../auth/accessToken.js';

/**
 * Sends a same-origin request with the in-memory bearer token and parses JSON, empty 204s, and API errors.
 * @param {string} path Gateway-relative API path
 * @param {RequestInit} [options] Fetch method, body, or abort signal
 * @returns {Promise<unknown>} Parsed response payload, or null for 204
 */
async function apiRequest(path, options = {}) {
  const headers = {
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {}),
    ...options.headers,
  };

  // Parse both JSON and empty 204 responses so Redux thunks can expose useful API errors.
  const response = await fetch(path, { ...options, headers });
  const text = response.status === 204 ? '' : await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { message: text };
    }
  }
  if (!response.ok) {
    if (response.status === 401 && getAccessToken()) {
      window.dispatchEvent(new Event('auth:unauthorized'));
    }
    throw new Error(body?.message || body?.error || `Request failed (${response.status})`);
  }
  return body;
}

/**
 * Loads one server-side page of orders.
 * @param {Record<string, string|number|undefined>} query Filter and page/sort parameters
 * @returns {Promise<object>} Page DTO with content and total-page metadata
 */
export const fetchOrders = createAsyncThunk('orders/fetchAll', async (query, { signal, rejectWithValue }) => {
  try {
    // Let the database filter/page the result instead of downloading every order into the browser.
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query ?? {})) {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    }
    return await apiRequest(`/api/orders?${params}`, { signal });
  } catch (error) {
    return rejectWithValue(error.message);
  }
});

/** @returns {Promise<object>} Global order counts and paid volume, independent of the selected list page. */
export const fetchOrderStatistics = createAsyncThunk('orders/fetchStatistics', async (_, { signal, rejectWithValue }) => {
  try {
    return await apiRequest('/api/orders/statistics', { signal });
  } catch (error) {
    return rejectWithValue(error.message);
  }
});

/**
 * Creates an order from form fields; server-owned ID/status/payment outcome are not accepted from the client.
 * @param {{userId: number, itemName: string, quantity: number, totalAmount: number}} order Validated form payload
 */
export const createOrder = createAsyncThunk('orders/create', async (order, { rejectWithValue }) => {
  try {
    return await apiRequest('/api/orders', {
      method: 'POST',
      body: JSON.stringify(order),
    });
  } catch (error) {
    return rejectWithValue(error.message);
  }
});

/**
 * Requests cancellation; server RBAC and state rules remain authoritative even if the UI exposes the action.
 * @param {number} id Persisted order ID
 */
export const cancelOrder = createAsyncThunk('orders/cancel', async (id, { rejectWithValue }) => {
  try {
    // The server permits this transition only to ADMIN; UI visibility never grants mutation permission.
    await apiRequest(`/api/orders/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: 'CANCELLED' }),
    });
    return id;
  } catch (error) {
    return rejectWithValue(error.message);
  }
});

// This slice owns server state and request lifecycle flags; components dispatch thunks instead of calling fetch directly.
/** Shared server state for order pages, summary metrics, pending requests, and user-visible errors. */
const ordersSlice = createSlice({
  name: 'orders',
  initialState: {
    items: [],
    statistics: null,
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    status: 'idle',
    error: null,
    saving: false,
    saveError: null,
    cancellingIds: [],
  },
  reducers: {
    dismissSaveError(state) {
      state.saveError = null;
    },
  },
  extraReducers(builder) {
    // Each async thunk drives explicit loading/success/error transitions so the UI can render retryable states.
    builder
      .addCase(fetchOrders.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(fetchOrders.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.items = action.payload.content;
        state.page = action.payload.number;
        state.size = action.payload.size;
        state.totalElements = action.payload.totalElements;
        state.totalPages = action.payload.totalPages;
      })
      .addCase(fetchOrders.rejected, (state, action) => {
        state.status = action.meta.aborted ? 'idle' : 'failed';
        state.error = action.meta.aborted ? null : action.payload || action.error.message;
      })
      .addCase(fetchOrderStatistics.fulfilled, (state, action) => {
        state.statistics = action.payload;
      })
      .addCase(createOrder.pending, (state) => {
        state.saving = true;
        state.saveError = null;
      })
      .addCase(createOrder.fulfilled, (state, action) => {
        state.saving = false;
        state.items.unshift(action.payload);
      })
      .addCase(createOrder.rejected, (state, action) => {
        state.saving = false;
        state.saveError = action.payload || action.error.message;
      })
      .addCase(cancelOrder.pending, (state, action) => {
        state.cancellingIds.push(action.meta.arg);
        state.saveError = null;
      })
      .addCase(cancelOrder.fulfilled, (state, action) => {
        state.cancellingIds = state.cancellingIds.filter((id) => id !== action.payload);
        const order = state.items.find((item) => item.id === action.payload);
        if (order) order.status = 'CANCELLED';
      })
      .addCase(cancelOrder.rejected, (state, action) => {
        state.cancellingIds = state.cancellingIds.filter((id) => id !== action.meta.arg);
        state.saveError = action.payload || action.error.message;
      });
  },
});

export default ordersSlice.reducer;
export const { dismissSaveError } = ordersSlice.actions;

const selectOrderState = (state) => state.orders;
// Memoized selectors keep component reads stable when unrelated store fields change.
/** Selects the current server page of order response DTOs. */
export const selectOrders = createSelector(selectOrderState, (orders) => orders.items);
/** Selects unfiltered global metrics for the dashboard summary. */
export const selectOrderStatistics = createSelector(selectOrderState, (orders) => orders.statistics);