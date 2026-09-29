import { expect, test } from '@playwright/test';

test('protects the order desk, validates the form, and handles an unavailable API', async ({ page }) => {
  await page.goto('/removed-page');
  await expect(page).toHaveURL(/\/login$/);

  await page.getByRole('button', { name: 'Open Order Desk' }).click();
  await expect(page).toHaveURL('http://127.0.0.1:5173/');
  await expect(page).toHaveURL('http://127.0.0.1:5173/');
  await expect(page.getByRole('heading', { name: 'Order desk.' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link')).toHaveCount(1);
  await expect(page.getByText('API unavailable')).toBeVisible();

  await page.getByRole('button', { name: 'Create order' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Add an item name.' })).toBeVisible();
});