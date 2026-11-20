import { useRef, useEffect, useMemo, useState, useCallback } from "react";
import { api } from "./lib.js";
import { Notice } from "./ui.jsx";

const TOOLBAR = "flex gap-3 items-center px-3.5 py-2";
const ROW = "flex gap-2.5 items-center px-2.5 py-[5px] border-b border-[#ddd]";
const INFO = "flex-1 whitespace-pre-line font-bold text-black";

const LOAD_FAILED = ["Kitchen Staff Load Failed", "The kitchen staff data could not be loaded.", "Please check the database connection and try again."];

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
};

/* ------------------------------ Ingredient Stock ------------------------------ */
//Same as manager's stock tab, but no ability to edit stock
// red below minimum, yellow up to 1.5x minimum, otherwise white
const stockColor = ({ stock, minStock }) =>
  stock < minStock ? "#FF3333" : stock <= Math.floor(minStock * 1.5) ? "#FFCC00" : "#FFFFFF";

function StockTab({ notify }) {
  const { data: items } = useApi("/manager/stock", notify);

  return (
    <ul>
      {items?.map((i) => (
        <li key={i.inventoryId} className={ROW} style={{ background: stockColor(i) }}>
          <span className={INFO}>
            {`#${i.inventoryId} ${i.name}:\n- Stock: ${i.stock}\n- Minimum Stock: ${i.minStock}\n- Next Shipment: ${i.nextShipment}\n- Shelf Life: ${i.shelfLife}`}
          </span>
        </li>
      ))}
    </ul>
  );
}

/* ------------------------------ Orders Tab ------------------------------ */
function TicketsTab({ notify }) {
    const { data: orders, setData } = useApi("/kitchen/orders", notify);
    const [busyIds, setBusyIds] = useState([]);

    // POST to complete an order, then remove it from the list
    const completeOrder = async (orderId) => {
        setBusyIds((current) => [...current, orderId]);

        try {
        await api(`/kitchen/orders/${orderId}/complete`, {
            method: "POST",
        });

        setData((current) =>
            current?.filter((order) => order.orderId !== orderId)
        );
        } catch (err) {
        notify(
            "Database Error",
            "Unable to complete order",
            err.message || "Please check the database connection and try again."
        );
        } finally {
        setBusyIds((current) =>
            current.filter((id) => id !== orderId)
        );
        }
    };

    //TODO uncomment when status is implemented
    //const pendingOrders = orders?.filter((order) => order.status === "PENDING");
    const displayedOrders = orders?.slice(0, 8);

    //loading messages if no orders or loading
    if (orders === null) {
        return <p>Loading tickets...</p>;
    }

    /*TODO uncomment when status is implemented
    if (pendingOrders.length === 0) {
        return <p>No pending orders.</p>;
    }*/
    if (displayedOrders.length === 0) {
    return <p>No orders.</p>;
    }

    //{pendingOrders.map((order) => { TODO: line 95
    return (
        <div className="w-full overflow-x-auto pb-4">
        <div className="flex items-stretch gap-4 w-max min-w-full">
            {displayedOrders.map((order) => {
            const busy = busyIds.includes(order.orderId);

            return (
                <div key={order.orderId}
                className="flex flex-col w-64 min-h-[300px] shrink-0 border border-gray-300 rounded-lg bg-white shadow-md">

                <div className="px-4 py-3 rounded-t-lg">
                <h2 className="text-xl font-bold text-black">#{order.orderId}</h2>
                <p className="text-sm text-gray-600">{order.timestamp}</p>
                </div>

                <div className="flex-1 p-4">
                <h3 className="font-bold text-gray-700 mb-2">Order Items</h3>
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
    ["Ingredient Stock", <StockTab notify={notify} />]
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