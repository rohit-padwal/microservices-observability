import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

async function apiRequest(path, options = {}) {
  const token = sessionStorage.getItem('fieldnotes.demo-session');
  const demoToken = token ? JSON.parse(token).token : null;
  const headers = {
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(demoToken ? { Authorization: `Bearer ${demoToken}` } : {}),
    ...options.headers,
  };

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
    throw new Error(body?.message || body?.error || `Request failed (${response.status})`);
  }
  return body;
}

export const fetchOrders = createAsyncThunk('orders/fetchAll', async (_, { signal, rejectWithValue }) => {
  try {
    return await apiRequest('/api/orders', { signal });
  } catch (error) {
    return rejectWithValue(error.message);
  }
});

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

export const cancelOrder = createAsyncThunk('orders/cancel', async (id, { rejectWithValue }) => {
  try {
    await apiRequest(`/api/orders/${id}/cancel`, { method: 'POST' });
    return id;
  } catch (error) {
    return rejectWithValue(error.message);
  }
});

const ordersSlice = createSlice({
  name: 'orders',
  initialState: {
    items: [],
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
    builder
      .addCase(fetchOrders.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(fetchOrders.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.items = action.payload;
      })
      .addCase(fetchOrders.rejected, (state, action) => {
        state.status = action.meta.aborted ? 'idle' : 'failed';
        state.error = action.meta.aborted ? null : action.payload || action.error.message;
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