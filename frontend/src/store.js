import { configureStore } from '@reduxjs/toolkit';
import ordersReducer from './features/orders/ordersSlice.js';

// Shared server-owned order data belongs in Redux; transient form edits remain local to components.
export const store = configureStore({
  reducer: {
    orders: ordersReducer,
  },
});