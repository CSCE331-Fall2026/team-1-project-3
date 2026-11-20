import { useEffect, useState } from "react";
import Cashier from "./Cashier.jsx";
import Manager from "./Manager.jsx";
import Customer from "./Customer.jsx"
import SignedIn from "./SignedIn.jsx";
import { currentUser, finishLogin, logout, startLogin } from "./auth.js";
import { api } from "./lib.js";

// Which views each role can open, anyone can use customer view
const ALLOWED_VIEWS = {
  Manager: ["manager", "cashier", "customer"],
  Cashier: ["cashier", "customer"]
};

// Replaces DatabaseApp + the two changeView() methods that swapped scenes
export default function App() {
  const [view, setView] = useState("customer");
  const [previousView, setPreviousView] = useState("customer");
  // starts from the saved token so refreshing keeps you logged in
  const [user, setUser] = useState(currentUser);
  let allowedViews = ["customer"];
  if (user && ALLOWED_VIEWS[user.perms] != null) {
    allowedViews = ALLOWED_VIEWS[user.perms];
  }

  // if we just came back from the login page, finish logging in and show the signed in page
  useEffect(() => {
    async function checkLogin() {
      const loggedIn = await finishLogin();
      if (loggedIn) {
        setUser(loggedIn);
        setView("signedIn");
      }
    }
    checkLogin();
  }, []);

  useEffect(() => {
    if (view === "customer") {
      document.title = "Customer View";
    }
    else if (view === "signedIn") {
      document.title = "Signed In";
    }
    else if (view === "cashier") {
      document.title = "Cashier View";
    }
    else {
      document.title = "Manager View";
    }
  }, [view]);

  // logged in, show the signed in page, otherwise show sign in page
  async function openLogin() {
    if (!currentUser()) {
      setUser(null);
      startLogin();
      return;
    }
    // AI generated, fixes from Pablo's review on PR #69 (old tokens after a server restart)
    // check the server still takes the token, if not api() already sent you to log in again
    try {
      setUser(await api("/me"));
    }
    catch {
      return;
    }
    setPreviousView(view);
    setView("signedIn");
  }

  function goBack() {
    setView(previousView);
  }

  // views your role, if cant use go back to customer, api blocks aswell
  if (view !== "signedIn" && !allowedViews.includes(view)) {
    return (<Customer onChangeView={openLogin} />);
  }

  if (view === "customer"){
    return (<Customer onChangeView={openLogin} />);
  }
  else if (view === "signedIn") {
    if (!user) {
      return (<Customer onChangeView={openLogin} />);
    }
    return (<SignedIn user={user} allowedViews={allowedViews} onPick={setView} onLogout={logout} onBack={goBack} />);
  }
  else if (view === "cashier") {
    return (<Cashier onChangeView={openLogin} />)
  }
  else {
    return (<Manager onChangeView={openLogin}/>)
  }
}
