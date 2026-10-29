import { useEffect, useState } from "react";
import Cashier from "./Cashier.jsx";
import Manager from "./Manager.jsx";

// Replaces DatabaseApp + the two changeView() methods that swapped FXML scenes.
export default function App() {
  const [view, setView] = useState("cashier");
  useEffect(() => {
    document.title = view === "cashier" ? "Cashier View" : "Manager View";
  }, [view]);
  return view === "cashier" ? (
    <Cashier onChangeView={() => setView("manager")} />
  ) : (
    <Manager onChangeView={() => setView("cashier")} />
  );
}
