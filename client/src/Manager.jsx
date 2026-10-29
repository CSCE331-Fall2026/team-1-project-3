import { useCallback, useEffect, useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { api, money, parsePrice, MAX_STOCK } from "./lib.js";
import { FormDialog, Notice } from "./ui.jsx";

const LOAD_FAILED = ["Manager Load Failed", "The manager data could not be loaded.",
  "Please check the database connection and try again."];

// Fetch-on-mount hook (replaces runQuery + the controllerActive guard).
function useApi(path, notify) {
  const [data, setData] = useState(null);
  const reload = useCallback(() => {
    const controller = new AbortController();
    api(path, { signal: controller.signal })
      .then(setData)
      .catch((err) => err.name !== "AbortError" && notify(...LOAD_FAILED));
    return () => controller.abort();
  }, [path]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => reload(), [reload]);
  return { data, setData, reload };
}

const dbError = (notify, op, details) => notify("Database Error", `Unable to ${op}`, details);

/* ------------------------------ Menu Items ------------------------------ */
function MenuTab({ notify, ask }) {
  const { data: items, setData, reload } = useApi("/manager/menu", notify);
  const [busyId, setBusyId] = useState(null);

  async function changePrice(item) {
    const v = await ask({
      title: "Change Price", header: "Enter the new price:", fields: [{ name: "price", label: "Price:" }],
    });
    if (!v) return;
    const cents = parsePrice(v.price);
    if (cents === null)
      return dbError(notify, "update price", "Enter a price from 0.00 to 99999999.99 with at most two decimal places.");
    setBusyId(item.itemId);
    try {
      const r = await api(`/manager/menu/${item.itemId}/price`, { method: "PATCH", body: { priceCents: cents } });
      setData((list) => list.map((i) => (i.itemId === item.itemId ? { ...i, priceCents: r.priceCents } : i)));
    } catch (err) {
      dbError(notify, "update price",
        err.status === 404 ? "The price value could not be updated." : "The database update failed.");
    } finally {
      setBusyId(null);
    }
  }

  async function removeItem(item) {
    setBusyId(item.itemId);
    try {
      const { removed } = await api(`/manager/menu/${item.itemId}`, { method: "DELETE" });
      if (removed) setData((list) => list.filter((i) => i.itemId !== item.itemId));
      else dbError(notify, "remove menu item", "The menu item was not active.");
    } catch {
      dbError(notify, "remove menu item", "The database update failed.");
    } finally {
      setBusyId(null);
    }
  }

  async function addItem() {
    const v = await ask({
      title: "Add Menu Item", header: "Enter the details for the new menu item:", submitLabel: "Add",
      fields: [
        { name: "itemId", label: "Item ID:" }, { name: "categoryId", label: "Category ID:" },
        { name: "nutrition", label: "Nutrition Info:" }, { name: "price", label: "Item Price:" },
        { name: "unitSize", label: "Unit Size:" }, { name: "name", label: "Item Name:" },
      ],
    });
    if (!v) return;
    const priceCents = parsePrice(v.price);
    if (!/^\d+$/.test(v.itemId.trim()) || !/^\d+$/.test(v.categoryId.trim()) || priceCents === null)
      return notify("Input Error", "Invalid Input",
        "Please provide valid item and category numbers and a price from 0.00 to 99999999.99 (max two decimals).");
    setBusyId("add");
    try {
      const { added } = await api("/manager/menu", {
        method: "POST",
        body: {
          itemId: Number(v.itemId), categoryId: Number(v.categoryId), nutrition: v.nutrition,
          priceCents, unitSize: v.unitSize, name: v.name,
        },
      });
      if (added) reload();
    } catch {
      dbError(notify, "add menu item", "The menu item was not added.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <div className="toolbar">
        <button disabled={busyId === "add"} onClick={addItem}>Add Menu Item</button>
      </div>
      <ul className="rows">
        {items?.map((i) => (
          <li key={i.itemId} className="row">
            <span className="info">{`#${i.itemId} ${i.name}:\n- Price: ${money(i.priceCents)}`}</span>
            <button disabled={busyId === i.itemId} onClick={() => changePrice(i)}>Change Price</button>
            <button disabled={busyId === i.itemId} onClick={() => removeItem(i)}>Remove Item</button>
          </li>
        ))}
      </ul>
    </>
  );
}

/* ------------------------------ Ingredient Stock ------------------------------ */
const sortStock = (list) => [...list].sort((a, b) => a.stock - b.stock || a.inventoryId - b.inventoryId);
// red below minimum, yellow up to 1.5x minimum, otherwise white
const stockColor = ({ stock, minStock }) =>
  stock < minStock ? "#FF3333" : stock <= Math.floor(minStock * 1.5) ? "#FFCC00" : "#FFFFFF";

function StockTab({ notify, ask }) {
  const { data: items, setData } = useApi("/manager/stock", notify);
  const [busyId, setBusyId] = useState(null);

  async function changeStock(item) {
    const v = await ask({
      title: "Change Stock", header: "Enter the change in stock (add a '-' if decreasing stock):",
      fields: [{ name: "delta", label: "Change in stock:" }],
    });
    if (!v) return;
    const text = v.delta.trim();
    if (!/^[+-]?\d+$/.test(text) || Math.abs(Number(text)) > MAX_STOCK)
      return dbError(notify, "update stock", "Enter a whole number no larger than 99999999 (negative to decrease).");
    const delta = Number(text);
    if (delta === 0) return;
    setBusyId(item.inventoryId);
    try {
      const r = await api(`/manager/stock/${item.inventoryId}`, { method: "PATCH", body: { delta } });
      if (r.stock !== null) {
        setData((list) => sortStock(list.map((i) => (i.inventoryId === item.inventoryId ? { ...i, stock: r.stock } : i))));
        if (!r.updated) dbError(notify, "update stock", `The stock value changed. Current stock: ${r.stock}`);
      } else if (r.missing) {
        setData((list) => list.filter((i) => i.inventoryId !== item.inventoryId));
        dbError(notify, "update stock", "The inventory record no longer exists.");
      }
    } catch {
      dbError(notify, "update stock", "The database update failed.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <ul className="rows">
      {items?.map((i) => (
        <li key={i.inventoryId} className="row" style={{ background: stockColor(i) }}>
          <span className="info">
            {`#${i.inventoryId} ${i.name}:\n- Stock: ${i.stock}\n- Minimum Stock: ${i.minStock}\n- Next Shipment: ${i.nextShipment}\n- Shelf Life: ${i.shelfLife}`}
          </span>
          <button disabled={busyId === i.inventoryId} onClick={() => changeStock(i)}>Change Stock</button>
        </li>
      ))}
    </ul>
  );
}

/* ------------------------------ Bar charts (Items Sold / Product Usage) ------------------------------ */
function ChartTab({ endpoint, title, rangePrefix, valueLabel, failure, notify, ask }) {
  const [chart, setChart] = useState({ title, rows: null });

  const load = useCallback(async (range) => {
    const qs = range ? `?start=${encodeURIComponent(range.start)}&end=${encodeURIComponent(range.end)}` : "";
    try {
      const rows = await api(`/manager/${endpoint}${qs}`);
      setChart({
        title: range ? `${rangePrefix} From ${range.start} to ${range.end}` : title,
        rows: rows.map((r) => ({ label: r.label ?? `#${r.id}-${r.name} ${r.value}`, value: r.value })),
      });
    } catch {
      notify(...(range ? failure : LOAD_FAILED));
    }
  }, [endpoint]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load(null); }, [load]);

  async function changePeriod() {
    const v = await ask({
      title: "Change Time Period", header: "Enter the start and end time periods (inclusive):",
      fields: [
        { name: "start", label: "Start:", placeholder: "ex. 2026-01-31 00:00:00" },
        { name: "end", label: "End:", placeholder: "ex. 2026-01-31 23:59:59" },
      ],
    });
    if (!v) return;
    const start = v.start.trim(), end = v.end.trim();
    if (!start || !end) return notify("Invalid Input", "Both start and end time periods are required.", "");
    load({ start, end });
  }

  return (
    <>
      <div className="toolbar">
        <button onClick={changePeriod}>Change Time Period</button>
        <h2>{chart.title}</h2>
      </div>
      {chart.rows && (
        <div className="chart" style={{ height: Math.max(320, chart.rows.length * 28 + 80) }}>
          <ResponsiveContainer>
            <BarChart data={chart.rows} layout="vertical" margin={{ left: 16, right: 24, bottom: 16 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} label={{ value: valueLabel, position: "insideBottom", offset: -8 }} />
              <YAxis type="category" dataKey="label" width={260} interval={0} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="value" fill="#D61927" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </>
  );
}

/* ------------------------------ Order History ------------------------------ */
function ReceiptsTab({ notify }) {
  const { data: rows } = useApi("/manager/receipts", notify);
  const receipts = [];
  for (const r of rows ?? []) {
    let g = receipts.at(-1);
    if (!g || g.id !== r.receiptId) receipts.push((g = { id: r.receiptId, timestamp: r.timestamp, items: [] }));
    g.items.push(r);
  }
  return (
    <div className="receipts">
      {receipts.map((g) => (
        <details key={g.id} name="receipts">
          <summary>Receipt {g.id}</summary>
          {g.items.map((r, i) => (
            <p key={i}>{`#${r.itemId} ${r.itemName}    x${r.quantity}    ${money(r.lineTotalCents)}`}</p>
          ))}
          <p>Total: {money(g.items.reduce((s, r) => s + r.lineTotalCents, 0))}</p>
          <p className="muted">{g.timestamp}</p>
        </details>
      ))}
    </div>
  );
}

/* ------------------------------ X Report ------------------------------ */
function XReportTab({ notify }) {
  const [rows, setRows] = useState(null);
  useEffect(() => {
    let alive = true;
    const refresh = () =>
      api("/manager/x-report")
        .then((r) => alive && setRows(r))
        .catch(() => alive && notify("X Report Graph Update Failed", "The X report graph could not be updated.", "Please try again."));
    refresh();
    // Refresh at the top of every hour (replaces the Timeline pair).
    const now = new Date();
    const untilNextHour = (60 - now.getMinutes()) * 60_000 - now.getSeconds() * 1000 - now.getMilliseconds();
    let interval;
    const timeout = setTimeout(() => {
      refresh();
      interval = setInterval(refresh, 3_600_000);
    }, untilNextHour);
    return () => { alive = false; clearTimeout(timeout); clearInterval(interval); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const data = rows?.map((r) => ({ label: `${r.hour}:00`, sales: r.orders, revenueCents: r.revenueCents }));
  return (
    <>
      <div className="toolbar"><h2>Sales Per Hour</h2></div>
      {data && (
        <div className="chart" style={{ height: 420 }}>
          <ResponsiveContainer>
            <LineChart data={data} margin={{ left: 8, right: 24, bottom: 24 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="label" label={{ value: "Hour of Operation", position: "insideBottom", offset: -12 }} />
              <YAxis allowDecimals={false} label={{ value: "Number of Sales", angle: -90, position: "insideLeft" }} />
              <Tooltip content={({ active, payload }) => {
                const p = active && payload?.[0]?.payload;
                return p ? (
                  <div className="tip"><strong>{p.label}</strong><br />Sales: {p.sales}<br />Revenue: {money(p.revenueCents)}</div>
                ) : null;
              }} />
              <Line type="monotone" dataKey="sales" stroke="#D61927" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </>
  );
}

/* ------------------------------ Shell ------------------------------ */
export default function Manager({ onChangeView }) {
  const [tab, setTab] = useState(0);
  const [notice, setNotice] = useState(null);
  const [dialog, setDialog] = useState(null);

  const notify = (title, header, details) => setNotice({ id: Date.now(), title, header, details });
  // Promise-based prompt: mirrors Dialog.showAndWait() so the tab code reads like the Java.
  const ask = (config) => new Promise((resolve) => setDialog({ ...config, resolve }));
  const closeDialog = (value) => { dialog.resolve(value); setDialog(null); };

  const tabs = [
    ["Menu Items", <MenuTab notify={notify} ask={ask} />],
    ["Ingredient Stock", <StockTab notify={notify} ask={ask} />],
    ["Product Usage", <ChartTab endpoint="stock-usage" title="Products Used in Whole Time Period"
      rangePrefix="Stock Used" valueLabel="Product Used" notify={notify} ask={ask}
      failure={["Stock Graph Update Failed", "The stock graph could not be updated.", "Please enter valid dates."]} />],
    ["Order History", <ReceiptsTab notify={notify} />],
    ["Items Sold", <ChartTab endpoint="sales" title="Items Sold in Whole Time Period"
      rangePrefix="Items Sold" valueLabel="Number Sold" notify={notify} ask={ask}
      failure={["Sales Graph Update Failed", "The sales graph could not be updated.", "Please enter valid dates."]} />],
    ["X Report", <XReportTab notify={notify} />],
  ];

  return (
    <div className="manager">
      <div className="toolbar"><button onClick={onChangeView}>Change View</button></div>
      <nav className="tabs" role="tablist">
        {tabs.map(([name], i) => (
          <button key={name} role="tab" aria-selected={tab === i} onClick={() => setTab(i)}>{name}</button>
        ))}
      </nav>
      {/* key remounts the tab, so each tab loads fresh data when opened, like the original */}
      <main key={tab}>{tabs[tab][1]}</main>

      {dialog && <FormDialog {...dialog} onSubmit={closeDialog} onCancel={() => closeDialog(null)} />}
      {notice && <Notice key={notice.id} notice={notice} onClose={() => setNotice(null)} />}
    </div>
  );
}
