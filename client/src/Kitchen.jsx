import { useRef, useEffect, useState, useCallback } from "react";
import { api } from "./lib.js";
import { Notice } from "./ui.jsx";

const TOOLBAR = "flex gap-3 items-center px-3.5 py-2";
const ROW = "flex gap-2.5 items-center px-2.5 py-[5px] border-b border-[#ddd]";
const INFO = "flex-1 whitespace-pre-line font-bold text-black";

const LOAD_FAILED = "Please check the database connection and try again.";
const POLL_MS = 10_000;
const TIMEOUT_MS = 15_000; // a hung request would otherwise block every later poll

// One request at a time: a reload asked for mid-request runs once right after it, so refreshes
// and polls never overlap and the newest request always answers last. After a failure or timeout
// the queued reload is dropped and the next poll retries, so a slow server is not hit back to back.
// stop() aborts and goes quiet.
export function createLoader(load, onResult, timeoutMs = TIMEOUT_MS) {
  let controller = null;
  let queued = false;
  let stopped = false;
  const reload = () => {
    if (stopped) return;
    if (controller) { queued = true; return; }
    const current = (controller = new AbortController());
    const timer = setTimeout(() => current.abort(new DOMException("The request timed out.", "TimeoutError")), timeoutMs);
    load(current.signal)
      .then(
        // api() turns an abort mid-body into a null success, so an aborted request never counts as data.
        (data) => !stopped && (current.signal.aborted ? onResult(current.signal.reason) : onResult(null, data)),
        (err) => {
          queued = false;
          if (!stopped && err.name !== "AbortError") onResult(err);
        })
      .finally(() => {
        clearTimeout(timer);
        controller = null;
        if (queued) { queued = false; reload(); }
      });
  };
  return { reload, stop() { stopped = true; controller?.abort(); } };
}

// Fetch-on-mount hook with loading/error state. `transform` runs on every successful response.
function useApi(path, transform = (d) => d) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const loader = useRef(null);
  const transformRef = useRef(transform);
  transformRef.current = transform;

  useEffect(() => {
    const l = createLoader((signal) => api(path, { signal }), (error, data) =>
      setState((s) => error ? { ...s, error, loading: false } : { data: transformRef.current(data), error: null, loading: false }));
    loader.current = l;
    l.reload();
    return l.stop; // tab switch / view change aborts the request in flight
  }, [path]);

  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true }));
    loader.current?.reload();
  }, []);
  const setData = useCallback((fn) => setState((s) => ({ ...s, data: fn(s.data) })), []);
  return { ...state, setData, reload };
}

// Refresh button plus a one-line status; shared by both tabs.
function LoadBar({ loading, error, hasData, reload }) {
  return (
    <div className="flex gap-3 items-center mb-3">
      <button onClick={reload} disabled={loading}>{loading ? "Refreshing..." : error ? "Retry" : "Refresh"}</button>
      {error && (
        <span role="alert" className="text-[#b00020] font-bold">
          {hasData ? "Could not refresh; showing the last loaded data. " : "Could not load data. "}{LOAD_FAILED}
        </span>
      )}
    </div>
  );
}

/* ------------------------------ Ingredient Stock ------------------------------ */
//Same as manager's stock tab, but no ability to edit stock
// red below minimum, yellow up to 1.5x minimum, otherwise white
const stockColor = ({ stock, minStock }) =>
  stock < minStock ? "#FF3333" : stock <= Math.floor(minStock * 1.5) ? "#FFCC00" : "#FFFFFF";

function StockTab() {
  const { data: items, loading, error, reload } = useApi("/kitchen/stock");

  return (
    <>
      <LoadBar loading={loading} error={error} hasData={items !== null} reload={reload} />
      {items === null ? (!error && <p>Loading stock...</p>) : items.length === 0 ? <p>No inventory items.</p> : (
        <ul>
          {items.map((i) => (
            <li key={i.inventoryId} className={ROW} style={{ background: stockColor(i) }}>
              <span className={INFO}>
                {`#${i.inventoryId} ${i.name}:\n- Stock: ${i.stock}\n- Minimum Stock: ${i.minStock}\n- Next Shipment: ${i.nextShipment}\n- Shelf Life: ${i.shelfLife}`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/* ------------------------------ Orders Tab ------------------------------ */
function TicketsTab({ notify }) {
    // Orders the backend confirmed completed. A poll that started before the confirmation could still
    // list them as PENDING, so every response is filtered through this set.
    const completed = useRef(new Set());
    const { data: orders, setData, loading, error, reload } = useApi("/kitchen/orders",
        (list) => list.filter((order) => !completed.current.has(order.orderId)));
    const submitting = useRef(new Set()); // synchronous guard; busyIds only drives rendering
    const [busyIds, setBusyIds] = useState([]);

    // New checkouts show up without staff touching anything; skipped while the browser tab is hidden.
    useEffect(() => {
        const id = setInterval(() => !document.hidden && reload(), POLL_MS);
        return () => clearInterval(id);
    }, [reload]);

    // POST to complete an order; remove it only once the backend confirms.
    const completeOrder = async (orderId) => {
        if (submitting.current.has(orderId)) return;
        submitting.current.add(orderId);
        setBusyIds((current) => [...current, orderId]);

        try {
            await api(`/kitchen/orders/${orderId}/complete`, { method: "POST" });
            completed.current.add(orderId);
            setData((current) => current?.filter((order) => order.orderId !== orderId));
        } catch (err) {
            if (err.status === 404) {
                // Don't guess: keep the ticket and ask the backend what is actually pending.
                notify("Order Not Found", `Order #${orderId} could not be found.`, "The pending orders are being refreshed.");
                reload();
            } else {
                notify("Database Error", "Unable to complete order", err.message || LOAD_FAILED);
            }
        } finally {
            submitting.current.delete(orderId);
            setBusyIds((current) => current.filter((id) => id !== orderId));
        }
    };

    const pendingOrders = orders?.filter((order) => order.status === "PENDING");
    const bar = <LoadBar loading={loading} error={error} hasData={orders !== null} reload={reload} />;

    //loading messages if no orders or loading
    if (orders === null) {
        return <>{bar}{!error && <p>Loading tickets...</p>}</>;
    }

    if (pendingOrders.length === 0) {
        return <>{bar}<p>No pending orders.</p></>;
    }

    return (
        <>
        {bar}
        <div className="w-full overflow-x-auto pb-4">
        <div className="flex items-stretch gap-4 w-max min-w-full">
            {pendingOrders.map((order) => {
            const busy = busyIds.includes(order.orderId);

            return (
                <div key={order.orderId}
                className="flex flex-col w-64 min-h-[300px] shrink-0 border border-gray-300 rounded-lg bg-white shadow-md">

                <div className="px-4 py-3 rounded-t-lg">
                <h2 className="text-xl font-bold text-black">#{order.orderId}</h2>
                <p className="text-sm text-gray-600">{order.timestamp}</p>
                </div>

                <div className="flex-1 p-4">
                <h3 className="font-bold text-black mb-2">Order Items</h3>
                <ul className="space-y-2">
                    {order.items.map((item, index) => (
                    <li key={`${item.itemId}-${index}`} className="flex justify-between gap-3 text-black border-b border-gray-100 pb-1">
                        <span>{item.name}</span>
                        <span className="font-bold shrink-0">
                            x{item.quantity}
                        </span>
                    </li>
                    ))}
                </ul>
                </div>

                <div className="flex justify-end p-3 border-t border-gray-200">
                    <button onClick={() => completeOrder(order.orderId)} disabled={busy}>
                        {busy ? "Completing..." : "Complete"}
                    </button>
                </div>

                </div>
            );
            })}

        </div>
        </div>
        </>
    );
}

/* ------------------------------ Shell ------------------------------ */
export default function Kitchen({ onChangeView }) {
  const [tab, setTab] = useState(0);
  const [notice, setNotice] = useState(null);
  const [dialog, setDialog] = useState(null);

  const notify = (title, header, details) => setNotice({ id: Date.now(), title, header, details });
  // Promise-based prompt: mirrors Dialog.showAndWait() so the tab code reads like the Java.
  const ask = (config) => new Promise((resolve) => setDialog({ ...config, resolve }));
  const closeDialog = (value) => { dialog.resolve(value); setDialog(null); };

  const tabs = [
    ["Pending Tickets", <TicketsTab notify={notify} />],
    ["Ingredient Stock", <StockTab />]
  ];

  return (
    <div className="h-screen flex flex-col">
      <div className={TOOLBAR}><button onClick={onChangeView}>Change View</button></div>
      <nav className="flex bg-brand" role="tablist">
        {tabs.map(([name], i) => (
          <button className="bg-transparent border-0 rounded-none px-4 py-2.5 text-[20px] font-bold text-[#d89490] hover:text-white aria-selected:text-white aria-selected:shadow-[inset_0_-3px_#fff]" key={name} role="tab" aria-selected={tab === i} onClick={() => setTab(i)}>{name}</button>
        ))}
      </nav>
      {/* key remounts the tab, so each tab loads fresh data when opened, like the original */}
      <main className="flex-1 overflow-auto p-3.5" key={tab}>{tabs[tab][1]}</main>

      {notice && <Notice key={notice.id} notice={notice} onClose={() => setNotice(null)} />}
    </div>
  );
}