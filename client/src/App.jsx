import { useEffect, useState } from "react";
import Cashier from "./Cashier.jsx";
import Manager from "./Manager.jsx";
import Customer from "./Customer.jsx"
import Login from "./Login.jsx";

// Which views each role can open, anyone can use customer view
const ALLOWED_VIEWS = {
  Manager: ["manager", "cashier", "customer"],
  Cashier: ["cashier", "customer"],
};

// Replaces DatabaseApp + the two changeView() methods that swapped scenes
export default function App() {
  const [view, setView] = useState("customer");
  const [previousView, setPreviousView] = useState("customer");
  const [user, setUser] = useState(null);
  let allowedViews = ["customer"];
  if (user && ALLOWED_VIEWS[user.perms] != null) {
    allowedViews = ALLOWED_VIEWS[user.perms];
  }

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
  }, [view]);

  const openLogin = () => {
    setPreviousView(view);
    setView("login");
  };

  const logout = () => {
    setUser(null);
    setView("customer");
  };
  
  //temporary "authentication" while I finalize how we will prevent people accessing stuff they shouldnt be able to
  if (view !== "login" && !allowedViews.includes(view)) {
    return (<Customer onChangeView={openLogin} />);
  }

  if (view === "customer"){
    return (<Customer onChangeView={openLogin} />);
  }
  else if (view === "login") {
    return (
      <Login
        user={user}
        allowedViews={allowedViews}
        onLogin={setUser}
        onPick={setView}
        onLogout={logout}
        onBack={() => setView(previousView)}
      />
    );
  }
  else if (view === "cashier") {
    return (<Cashier onChangeView={openLogin} />)
  }
  else {
    return (<Manager onChangeView={openLogin}/>)
  }
}
