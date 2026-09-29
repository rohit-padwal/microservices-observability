import { expect, test } from '@playwright/test';

test('protects the order desk, validates the form, and handles an unavailable API', async ({ page }) => {
  let authorizationHeader;
  await page.route('**/api/auth/login', async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      accessToken: 'signed-token-from-test-server',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      username: 'operator',
      roles: ['OPERATOR'],
    }),
  }));
  await page.route('**/api/orders?*', async (route) => {
    authorizationHeader = route.request().headers().authorization;
    await route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Order Service unavailable"}' });
  });
  await page.route('**/api/orders/statistics', async (route) =>
    route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Order Service unavailable"}' }));

  await page.goto('/removed-page');
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('Username').fill('operator');
  await page.getByLabel('Password').fill('a-secure-password');
  await page.getByRole('button', { name: 'Open Order Desk' }).click();
  await expect(page).toHaveURL('http://127.0.0.1:5173/');
  await expect(page).toHaveURL('http://127.0.0.1:5173/');
  await expect(page.getByRole('heading', { name: 'Order desk.' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link')).toHaveCount(1);
  await expect(page.getByText('API unavailable')).toBeVisible();
  await expect.poll(() => authorizationHeader).toBe('Bearer signed-token-from-test-server');
  expect(await page.evaluate(() => sessionStorage.length)).toBe(0);

  await page.getByRole('button', { name: 'Create order' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Add an item name.' })).toBeVisible();
});

test('rejects invalid backend credentials without opening a session', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) =>
    route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"Invalid username or password"}' }));
  await page.goto('/login');
  await page.getByLabel('Username').fill('operator');
  await page.getByLabel('Password').fill('incorrect-password');
  await page.getByRole('button', { name: 'Open Order Desk' }).click();
  await expect(page.getByRole('alert')).toContainText('Invalid username or password');
  await expect(page).toHaveURL(/\/login$/);
});

test('denies authenticated users without an operations role', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ accessToken: 'customer-token', expiresAt: new Date(Date.now() + 60_000).toISOString(), username: 'customer', roles: ['CUSTOMER'] }),
  }));
  await page.goto('/login');
  await page.getByLabel('Username').fill('customer');
  await page.getByLabel('Password').fill('customer-password');
  await page.getByRole('button', { name: 'Open Order Desk' }).click();
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
});

test('clears the in-memory session when a protected API returns 401', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ accessToken: 'expired-token', expiresAt: new Date(Date.now() + 60_000).toISOString(), username: 'operator', roles: ['OPERATOR'] }),
  }));
  await page.route('**/api/orders?*', async (route) =>
    route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"Unauthorized"}' }));
  await page.route('**/api/orders/statistics', async (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"totalOrders":0,"paidOrders":0,"paidVolume":0}' }));
  await page.goto('/login');
  await page.getByLabel('Username').fill('operator');
  await page.getByLabel('Password').fill('operator-password');
  await page.getByRole('button', { name: 'Open Order Desk' }).click();
  await expect(page).toHaveURL(/\/login$/);
});