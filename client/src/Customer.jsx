import { useRef, useEffect, useMemo, useState } from "react";
import {
  api, CATEGORY, MEAL_SIZES, money,
  singleLine, mealLine, lineTotal, describeLine, flattenCart,
} from "./lib.js";
import { Notice } from "./ui.jsx";


const LOCATION_ID = 1; // TODO: take from login/session instead of constants
const USER_ID = 3;


const GRID = "flex grid grid-cols-3 gap-[5px] border border-[grey] p-[15px] overflow-auto min-w-0";
const GRID_TITLE = "p-2.5 text-[22px] text-center font-bold w-full";
const COL = "flex flex-col gap-[5px] border border-[grey] p-[15px] overflow-auto min-w-0";
const COL_TITLE = "p-2.5 text-[22px] text-center font-bold";
const SELECTED = "border-2 border-brand font-bold";


export default function Customer({ onChangeView }) {
  const [menu, setMenu] = useState([]);
  const [menuStatus, setMenuStatus] = useState("loading"); // loading | ready | error
  const [selectedSize, setSelectedSize] = useState(null);
  const [pendingMains, setPendingMains] = useState([]);
  const [pendingSide, setPendingSide] = useState(null);
  const [cartLines, setCartLines] = useState([]);
  // idle | inProgress | uncertain (replaces checkoutInProgress / checkoutOutcomeUncertain)
  const [checkoutState, setCheckoutState] = useState("idle");
  const [notice, setNotice] = useState(null);


  const [tab, setTab] = useState(0);
  const mealRef = useRef(null);
  const mainsRef = useRef(null);
  const sidesRef = useRef(null);
  const drinksRef = useRef(null);
  const cartRef = useRef(null);
  const tabs = [["Meal", mealRef], ["Mains", mainsRef], ["Sides", sidesRef], ["Drinks", drinksRef], ["Cart", cartRef]];




  const say = (title, header, details) => setNotice({ id: Date.now(), title, header, details });
  const locked = checkoutState !== "idle";
  const total = useMemo(() => cartLines.reduce((sum, l) => sum + lineTotal(l), 0), [cartLines]);


  // runQuery/loadBoxes/loadBoxesFail. Aborting on unmount replaces `controllerActive`.
  useEffect(() => {
    const controller = new AbortController();
    api("/menu", { signal: controller.signal })
      .then((rows) => {
        setMenu(rows);
        setMenuStatus("ready");
      })
      .catch((err) => {
        if (err.name === "AbortError") return;
        setMenuStatus("error");
        say("Menu Load Failed", "The menu could not be loaded.",
          "Please check the database connection and try again.");
      });
    return () => controller.abort();
  }, []);


  // Highlights are derived from state, so clearHighlights() disappears.
  const isSelected = (r) =>
    (r.categoryId === CATEGORY.MAIN && pendingMains.some((m) => m.itemId === r.itemId)) ||
    (r.categoryId === CATEGORY.SIDE && pendingSide?.itemId === r.itemId);


  const resetPending = () => {
    setPendingMains([]);
    setPendingSide(null);
  };


  const selectSize = (size) => {
    setSelectedSize((cur) => (cur === size ? null : size));
    resetPending();
  };


  const addToCart = (line) =>
    setCartLines((lines) => {
      const i = line.key === null ? -1 : lines.findIndex((l) => l.key === line.key);
      if (i === -1) return [...lines, line];
      return lines.map((l, j) => (j === i ? { ...l, quantity: l.quantity + 1 } : l));
    });


  const pressMenuItem = (record) => {
    if (!selectedSize || record.categoryId === CATEGORY.DRINK) return addToCart(singleLine(record));
    const needed = MEAL_SIZES[selectedSize].mains;
    let mains = pendingMains;
    let side = pendingSide;
    if (record.categoryId === CATEGORY.MAIN && mains.length < needed) mains = [...mains, record];
    if (record.categoryId === CATEGORY.SIDE) side = record;


    if (mains.length === needed && side) {
      addToCart(mealLine(selectedSize, mains, side));
      setSelectedSize(null); // the original calls selectSize(selectedSize), which toggles it off
      resetPending();
    } else {
      setPendingMains(mains);
      setPendingSide(side);
    }
  };


  const changeQuantity = (id, delta) =>
    setCartLines((lines) =>
      lines.map((l) => (l.id === id ? { ...l, quantity: l.quantity + delta } : l)).filter((l) => l.quantity >= 1)
    );
  const removeLine = (id) => setCartLines((lines) => lines.filter((l) => l.id !== id));


  const confirmCheckout = async () => {
    if (!cartLines.length || locked) return;
    setCheckoutState("inProgress");
    const uncertain = () => {
      say("Checkout Status Unknown", "The order status could not be confirmed.",
        "Do not retry. Verify the receipt with a manager before submitting another order.");
      setCheckoutState("uncertain"); // stays locked, like the original
    };
    try {
      const { outcome } = await api("/checkout", {
        method: "POST",
        body: { locationId: LOCATION_ID, userId: USER_ID, items: flattenCart(cartLines) },
      });
      if (outcome === "UNCERTAIN") return uncertain();
      if (outcome === "SUCCESS") {
        setCartLines([]);
        say("Order Complete", null, "Order recorded successfully.");
      } else {
        say("Checkout Failed", "The order was not recorded.", "Please verify inventory and try again.");
      }
    } catch (err) {
      // No HTTP response at all (dropped connection): the order may or may not have been saved.
      if (!err.status) return uncertain();
      say("Checkout Failed", "The order was not recorded.", "Please verify inventory and try again.");
    }
    setCheckoutState("idle");
  };


  const column = (category, label, ref) => (
    <section ref={ref} className={COL}>
      <h3 className={COL_TITLE}>{label}</h3>
      <div >
        {menu.filter((r) => r.categoryId === category).map((r) => (
        <button key={r.itemId} className={isSelected(r) ? SELECTED : ""} onClick={() => pressMenuItem(r)}>
          {r.name}
        </button>
      ))}
      </div>
    </section>
  );

  const grid = (category, label, ref) => (
    <section ref={ref} className="flex flex-col">
      <h3 className={GRID_TITLE}>{label}</h3>
      <div className={GRID}>
        {menu.filter((r) => r.categoryId === category).map((r) => (
        <button key={r.itemId} className={isSelected(r) ? SELECTED : ""} onClick={() => pressMenuItem(r)}>
          {r.name}
        </button>
      ))}
      </div>
    </section>
  );

  const scrollToSection = (i) => {
    setTab(i);
    tabs[i][1].current?.scrollIntoView({behavior: "smooth", block: "start"});
  };


  return (
    <div className="flex h-dvh overflow-hidden [&_button]:text-[20px] [&_button]:min-h-[30px]">
 
      <nav className="flex flex-col bg-brand" role="tablist">
        {tabs.map(([name], i) => (
          <button className="bg-transparent border-0 rounded-none px-4 py-2.5 text-[20px]
          font-bold text-[#d89490] hover:text-white aria-selected:text-white"
          key={name}
          role="tab"
          aria-selected={tab === i}
          onClick={() => scrollToSection(i)}>
            {name}
          </button>
        ))}
        <div className="p-3"> <button onClick={onChangeView}>Change View</button> </div>
      </nav>
      
      <div className="w-full flex-1 min-h-0 overflow-y-auto"> 
        <h3 className={GRID_TITLE}>Meal</h3>
        <section ref={mealRef} className={GRID}>
          {Object.entries(MEAL_SIZES).map(([size, { label }]) => (
            <button key={size} className={selectedSize === size ? SELECTED : ""} onClick={() => selectSize(size)}>
              {label}
            </button>
          ))}
        </section>


        {/* <fieldset disabled> replaces the four setDisable(true) calls */}
        <fieldset className="contents" disabled={locked}>
          {grid(CATEGORY.MAIN, "Mains", mainsRef)}
          {grid(CATEGORY.SIDE, "Sides", sidesRef)}
          {grid(CATEGORY.DRINK, "Drinks", drinksRef)}
        </fieldset>


        <aside ref={cartRef} className="flex flex-col gap-[5px] min-h-0">
          <fieldset className={`${COL} flex-1 items-stretch`} disabled={locked}>
            {cartLines.map((line) => (
              <div className="flex gap-[5px] items-center" key={line.id}>
                {/* Unchecking removes the line, as in the original */}
                <label className="flex-1 text-[20px]">
                  <input type="checkbox" checked onChange={() => removeLine(line.id)} />
                  {describeLine(line)}
                </label>
                <button onClick={() => changeQuantity(line.id, -1)}>-</button>
                <button onClick={() => changeQuantity(line.id, 1)}>+</button>
              </div>
            ))}
            {menuStatus === "ready" && <p className="mt-2.5 text-[22px] font-bold">Total Price: {money(total)}</p>}
          </fieldset>
          <button disabled={locked || menuStatus === "loading" || !cartLines.length} onClick={confirmCheckout}>
            Checkout
          </button>
        </aside>
      </div>


      {notice && <Notice key={notice.id} notice={notice} onClose={() => setNotice(null)} />}
    </div>
  );
}



