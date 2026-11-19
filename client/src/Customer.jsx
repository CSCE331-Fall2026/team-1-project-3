import { useRef, useEffect, useMemo, useState } from "react";
import {
  api, CATEGORY, MEAL_SIZES, money,
  singleLine, mealLine, lineTotal, describeLine, flattenCart,
} from "./lib.js";
import { Notice } from "./ui.jsx";


const LOCATION_ID = 1; // TODO: take from login/session instead of constants
const USER_ID = 3;


const GRID = "flex grid grid-cols-3 gap-[5px] border-t border-gray-300 p-[15px] overflow-auto min-w-0";
const GRID_TITLE = "p-2.5 text-[22px] text-center font-bold w-full";
const MEAL_GRID_TITLE = "p-2.5 text-[22px] text-center font-bold w-full flex items-center justify-between";
const COL = "flex flex-col gap-[5px] border border-gray-300 p-[15px] overflow-auto min-w-0";
const BUTTON_TWO = "bg-brand px-4 py-2 text-white hover:bg-brand2 border-none";
const MENU_TILE = "border-1 shadow-lg border-gray-200 flex flex-col items-center gap-2 rounded-[10px] hover:border-brand hover:shadow-none";
const SELECTED = "border-2 border-brand flex flex-col items-center rounded-[10px]";
const INFO = "absolute top-1 left-1 rounded-full border-gray-500 text-gray-500 !text-[10px] !min-h-0 !px-1.5 !py-0.75 hover:border-gray-900 hover:text-gray-900 hover:font-bold";
const CLOSE = "absolute top-1 right-1 border-none text-gray-500" 


export default function Customer({ onChangeView }) {
  const [menu, setMenu] = useState([]);
  const [menuStatus, setMenuStatus] = useState("loading"); // loading | ready | error
  const [selectedSize, setSelectedSize] = useState(null);
  const [pendingMains, setPendingMains] = useState([]);
  const [pendingSide, setPendingSide] = useState(null);
  const [cartLines, setCartLines] = useState([]);
  const [mealDetails, setMealDetails] = useState(false);
  const [nutritionDetails, setNutritionDetails] = useState(false);
  const [itemDetails, setItemDetails] = useState(null);
  const [pendingScrollIndex, setPendingScrollIndex] = useState(null);
  // idle | inProgress | uncertain (replaces checkoutInProgress / checkoutOutcomeUncertain)
  const [checkoutState, setCheckoutState] = useState("idle");
  const [notice, setNotice] = useState(null);


  const [tab, setTab] = useState(0);
  const mealRef = useRef(null);
  const mainsRef = useRef(null);
  const sidesRef = useRef(null);
  const drinksRef = useRef(null);
  const cartRef = useRef(null);
  const tabs = [["Meal", mealRef], ["Mains", mainsRef], ["Sides", sidesRef], ["Drinks", drinksRef]];




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
    setMealDetails(!mealDetails);
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

    if (isSelected(record)){
      if (record.categoryId === CATEGORY.MAIN){
        setPendingMains((cur) => cur.filter((m) => m.itemId !== record.itemId));
      } else {
        setPendingSide(null);
      }
      return
    }

    const needed = MEAL_SIZES[selectedSize].mains;
    let mains = pendingMains;
    let side = pendingSide;
    if (record.categoryId === CATEGORY.MAIN) {
      if (mains.length < needed) mains = [...mains, record];
      else if (needed === 1) mains = [...mains.slice(1), record]
    }
    if (record.categoryId === CATEGORY.SIDE) side = record;

    setPendingMains(mains);
    setPendingSide(side);
  };

  const addPendingMealToCart = () => {
    if (!selectedSize) return;

    const needed = MEAL_SIZES[selectedSize].mains;

    if (pendingMains.length !== needed || !pendingSide) return;

    addToCart(mealLine(selectedSize, pendingMains, pendingSide));

    setSelectedSize(null);
    resetPending();
    setMealDetails(false);
  }

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

  const mealProgressText = (category) => {
    if (!selectedSize) return null;
    
    if (category === CATEGORY.MAIN) {
      const needed = MEAL_SIZES[selectedSize].mains;
      return `${pendingMains.length}/${needed} main${needed > 1 ? "s" : ""}`;
    }
    
    if (category === CATEGORY.SIDE) {
      return `${pendingSide ? 1 : 0}/1 side`;
    }
    
    return null;
  };

  const grid = (category, label, ref) => (
    <section ref={ref} className="flex flex-col">
      <h3 className={GRID_TITLE}>{label}</h3>
      <div className={GRID}>
        {menu.filter((r) => r.categoryId === category).map((r) => (
        <div key={r.itemId} className={`${MENU_TILE} relative`} onClick={() => setItemDetails(r)}>
          <button className={INFO}
          onClick={(e) => { e.stopPropagation(); setNutritionDetails(r);}}>i</button>
          <img src="" className="h-20 w-20 object-contain"/>
          <span>{r.name}</span>
        </div>
      ))}
      </div>
    </section>
  );

  const mealGrid = (category, label, ref) => {
    const progress = mealProgressText(category);
    return (
    <section ref={ref} className="flex flex-col">
      <h3 className={MEAL_GRID_TITLE}>{label}
        {progress && (
          <span className="text-[18px] font-normal">
            {progress}
          </span>
        )}
      </h3>
      <div className={GRID}>
        {menu.filter((r) => r.categoryId === category).map((r) => (
        <div key={r.itemId} className={`${isSelected(r) ? SELECTED : MENU_TILE} relative`} onClick={() => pressMenuItem(r)}>
          <button className={INFO}
          onClick={(e) => { e.stopPropagation(); setNutritionDetails(r);}}>i</button>
          <img src="" className="h-20 w-20 object-contain"/>
          <span>{r.name}</span>
        </div>
      ))}
      </div>
    </section>
    );
  };

  const scrollToSection = (i) => {
    setTab(i);
    tabs[i][1].current?.scrollIntoView({behavior: "smooth", block: "start"});
  };

  const goToSection = (i) => {
    if (mealDetails) {
      selectSize(null);
      resetPending();
      setMealDetails(null);
      setPendingScrollIndex(i);
      return;
    }
    
    scrollToSection(i);
  }

  return (
    <div className="flex h-dvh overflow-hidden [&_button]:text-[20px] [&_button]:min-h-[30px]">
 
      <nav className="flex flex-col bg-brand" role="tablist">
        {tabs.map(([name], i) => (
          <button className="bg-transparent border-0 rounded-none px-4 py-2.5 text-[20px]
          font-bold text-[#d89490] hover:text-white aria-selected:text-white"
          key={name}
          role="tab"
          aria-selected={tab === i}
          onClick={() => goToSection(i)}>
            {name}
          </button>
        ))}
        <div className="p-3"> <button onClick={onChangeView}>Change View</button> </div>
      </nav>
      
      
      <main className="relative flex-1 min-w-0 min-h-0 overflow-y-auto">
        {mealDetails ? (
        <div className="">
        <fieldset className="contents" disabled={locked}>
          {mealGrid(CATEGORY.MAIN, "Mains", mainsRef)}
          {mealGrid(CATEGORY.SIDE, "Sides", sidesRef)}
          <div className="border-t-1 border-gray-300 sticky inset-x-0 bottom-0 z-10 p-4 bg-white">
            <div className="flex w-full justify-between items-center gap-4 rounded px-6 py-3 text-black">
              <span className="font-bold text-2xl">
                {selectedSize}
              </span>
              <div>
                <button className="border-white" onClick={() => goToSection(0)}>
                  Back
                </button>
                <button className={BUTTON_TWO} onClick={() => addPendingMealToCart()}>
                Add to Cart {
                  selectedSize && pendingMains.length > 0 && pendingSide ? 
                  money(mealLine(selectedSize, pendingMains, pendingSide).unitCents) :
                  ""
                }
                </button>
              </div>
            </div>
          </div>
        </fieldset>
        </div> ) : 
        ( <>
        <section ref={mealRef} className="flex flex-col">
          <h3 className={GRID_TITLE}>Meals</h3>
          <div className={GRID}>
          {Object.entries(MEAL_SIZES).map(([size, { label }]) => (
            <button key={size} className={MENU_TILE} onClick={() => selectSize(size)}>
              <img src="" className="h-20 w-20 object-contain"/>
              <span>{label}</span>
            </button>
          ))}
          </div>
        </section>


        {/* <fieldset disabled> replaces the four setDisable(true) calls */}
        <fieldset className="contents" disabled={locked}>
          {grid(CATEGORY.MAIN, "Mains", mainsRef)}
          {grid(CATEGORY.SIDE, "Sides", sidesRef)}
          {grid(CATEGORY.DRINK, "Drinks", drinksRef)}
        </fieldset> </>)}
        </main>

        <aside className="flex w-[360px] shrink-0 flex-col gap-[5px] min-h-0 overflow-y-auto p-3">
          <fieldset className={`${COL} flex-1 items-stretch`} disabled={locked}>
            {menuStatus === "ready" && <p className="mt-2.5 text-[22px] font-bold">Total Price: {money(total)}</p>}
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
          </fieldset>
          <button className={BUTTON_TWO} disabled={locked || menuStatus === "loading" || !cartLines.length} onClick={confirmCheckout}>
            Checkout
          </button>
        </aside>

      {notice && <Notice key={notice.id} notice={notice} onClose={() => setNotice(null)} />}
      {itemDetails && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setItemDetails(null)}>
          <div className="rounded-[10px] text-[20px] bg-white p-6 flex flex-col gap-3 relative"> 
            <div className="border-b border-gray-300 py-3">{itemDetails.name}</div>
            <button className={BUTTON_TWO} onClick={() => {
              pressMenuItem(itemDetails); 
              setItemDetails(null);
            }}>
              Add to Cart {money(itemDetails.priceCents)}
            </button>
            <button className={CLOSE} onClick={() => {setItemDetails(null);}}>
              X
            </button> 
          </div>
        </div>
      )}
      {nutritionDetails && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setNutritionDetails(null)}>
          <div className="rounded-[10px] text-[20px] bg-white p-6 flex flex-col gap-3 relative"> 
            <div className="border-b border-gray-300 py-3">{nutritionDetails.name}</div>
            <div className="text-[15px] whitespace-pre-line">{nutritionDetails.nutrition}</div>
            <button className={CLOSE} onClick={() => {setNutritionDetails(null);}}>
              X
            </button> 
          </div>
        </div>
      )}
    </div>
  );
}



