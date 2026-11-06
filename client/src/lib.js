// Shared helpers: API client, money (integer cents instead of BigDecimal),
// MealSize, and the CartLine / flattenCart logic.

export const CATEGORY = { MAIN: 1, SIDE: 2, DRINK: 3 };
export const MAX_PRICE_CENTS = 9_999_999_999;
export const MAX_STOCK = 99_999_999;

export const money = (cents) => `$${(cents / 100).toFixed(2)}`;

// "5", "5.5", "5.50" -> cents; null if invalid or out of range.
export function parsePrice(text) {
  const t = text.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const cents = Math.round(parseFloat(t) * 100);
  return cents <= MAX_PRICE_CENTS ? cents : null;
}

export async function api(path, { method = "GET", body, signal } = {}) {
  const res = await fetch(`/api${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    signal,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    // err.status is only set when the server actually answered.
    const err = new Error(data?.error ?? res.statusText);
    err.status = res.status;
    throw err;
  }
  return data;
}

// MealSize enum. `discount` is the original BigDecimal multiplier scaled by 1e5 (0.39068 -> 39068)
// so the price math stays in integers.
export const MEAL_SIZES = {
  BOWL: { label: "Bowl", mains: 1, discount: 39068 },
  PLATE: { label: "Plate", mains: 2, discount: 54839 },
  BIGGER_PLATE: { label: "Bigger Plate", mains: 3, discount: 61598 },
};

// CartLine. `key` is null for meals, so (as in the Java) only single items merge.
let nextLineId = 1;
const makeLine = (key, label, unitCents, components) => ({
  id: nextLineId++, // stable React key
  key,
  label,
  unitCents,
  components,
  quantity: 1,
});

export const singleLine = (r) => makeLine(`item:${r.itemId}`, r.name, r.priceCents, [r]);

export function mealLine(size, mains, side) {
  const components = [...mains, side];
  const list = components.reduce((sum, c) => sum + c.priceCents, 0);
  const { label, discount } = MEAL_SIZES[size];
  const unit = Math.floor((list * (100000 - discount) + 50000) / 100000); // HALF_UP at 2 decimals
  return makeLine(null, label, unit, components);
}

export const lineTotal = (l) => l.unitCents * l.quantity;
const lineDiscount = (l) => l.components.reduce((s, c) => s + c.priceCents, 0) - l.unitCents;

export const describeLine = (l) =>
  `${l.quantity}x ${l.label}` +
  (l.components.length > 1 ? ` (${l.components.map((c) => c.name).join(", ")})` : "") +
  ` ${money(lineTotal(l))}`;

// Port of flattenCart(): per-item quantity / list price / total, with the meal discount
// spread across the proteins (everything but the trailing side) so cents always add up.
export function flattenCart(lines) {
  const items = new Map();
  for (const line of lines) {
    const proteins = line.components.length - 1;
    const cents = lineDiscount(line);
    line.components.forEach((c, i) => {
      let unit = c.priceCents;
      if (i < proteins) {
        const base = Math.floor(cents / proteins);
        unit -= i === proteins - 1 ? cents - base * (proteins - 1) : base;
      }
      const e = items.get(c.itemId) ?? {
        itemId: c.itemId,
        quantity: 0,
        listPriceCents: c.priceCents,
        totalCents: 0,
      };
      e.quantity += line.quantity;
      e.totalCents += unit * line.quantity;
      items.set(c.itemId, e);
    });
  }
  return [...items.values()];
}
