import { configureStore } from '@reduxjs/toolkit';
import ordersReducer from './features/orders/ordersSlice.js';

/** Root Redux store for shared server-owned state; transient form drafts remain local to components. */
export const store = configureStore({
  reducer: {
    orders: ordersReducer,
  },
});