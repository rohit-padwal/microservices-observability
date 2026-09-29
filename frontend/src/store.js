import { configureStore } from '@reduxjs/toolkit';
import ordersReducer from './features/orders/ordersSlice.js';

export const store = configureStore({
  reducer: {
    orders: ordersReducer,
  },
});