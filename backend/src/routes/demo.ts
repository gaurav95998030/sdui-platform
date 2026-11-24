import { Router } from 'express';

export const demoRouter: Router = Router();

/**
 * Stand-in business APIs for the demo app.
 *
 * They exist so the `apiCall` action, pagination and cache policies can be
 * exercised end to end without a separate backend. Nothing in the SDK depends on
 * them — a real deployment points `screen.apis[*].url` at its own services.
 */

const CATEGORIES = ['Food', 'Transport', 'Subscriptions', 'Income', 'Health'] as const;
const ICONS = ['cart', 'creditCard', 'play', 'download', 'favorite'] as const;
const MERCHANTS = [
  'Blue Bottle Coffee', 'Metro Card Top-up', 'Spotify Family', 'Salary — August',
  'Apollo Pharmacy', 'Uber Trip', 'Amazon Fresh', 'Netflix', 'Gym Membership',
  'Electricity Bill', 'Book Depot', 'Cafe Noir',
];

const PAGE_SIZE = 6;

demoRouter.get('/transactions', (req, res) => {
  const page = Math.max(1, Number(req.query.page ?? 1));
  const start = (page - 1) * PAGE_SIZE;

  const items = Array.from({ length: PAGE_SIZE }, (_, i) => {
    const index = start + i;
    const income = index % 5 === 3;
    return {
      id: `t${index + 1}`,
      title: MERCHANTS[index % MERCHANTS.length],
      category: income ? 'Income' : CATEGORIES[index % CATEGORIES.length],
      icon: income ? 'download' : ICONS[index % ICONS.length],
      amount: income ? 185000 : -((index % 9) + 1) * 149,
      date: new Date(Date.UTC(2026, 7, Math.max(1, 28 - index))).toISOString().slice(0, 10),
    };
  });

  res.json({
    items,
    page,
    pageSize: PAGE_SIZE,
    hasMore: page < 5,
    total: PAGE_SIZE * 5,
  });
});

demoRouter.post('/expenses', (req, res) => {
  const { amount, category, date, note, billable } = req.body ?? {};
  if (amount === undefined || amount === null || String(amount).length === 0) {
    return res.status(422).json({ error: 'amount is required' });
  }
  res.status(201).json({
    id: `e_${Math.random().toString(36).slice(2, 9)}`,
    amount: Number(amount),
    category: category ?? 'food',
    date: date ?? new Date().toISOString().slice(0, 10),
    note: note ?? '',
    billable: billable === true || billable === 'true',
    createdAt: new Date().toISOString(),
  });
});

demoRouter.get('/profile', (_req, res) => {
  res.json({
    name: 'Ada Lovelace',
    firstName: 'Ada',
    email: 'ada@example.com',
    isPremium: true,
    avatar: 'https://i.pravatar.cc/160?img=47',
    wallet: { balance: 42150.75, currency: 'INR' },
  });
});
