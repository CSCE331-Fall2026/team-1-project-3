import { useEffect, useState } from "react";
import Cashier from "./Cashier.jsx";
import Manager from "./Manager.jsx";
import Customer from "./Customer.jsx"
import Login from "./Login.jsx";

// Replaces DatabaseApp + the two changeView() methods that swapped FXML scenes.
export default function App() {
  const [view, setView] = useState("customer");
  useEffect(() => {
    if (view === "customer") {
      document.title = "Customer View";
    }
    else if (view === "login") {
      document.title = "Login";
    }
    else if (view === "cashier") {
      document.title = "Cashier View";
    }
    else {
      document.title = "Manager View";
    }
    // document.title = view === "customer" ? "Customer View" : "Cashier View";
  }, [view]);
  if (view === "customer"){
    return (<Customer onChangeView={() => setView("login")} />);
  }
  else if (view === "login") {
    return (<Login />);
  }
  else if (view === "cashier") {
    return (<Cashier onChangeView={() => setView("login")} />)
  }
  else {
    return (<Manager onChangeView={() => setView("login")}/>)
  }
}
