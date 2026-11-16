import { useState } from "react";
import { api } from "./lib.js";

const VIEW_LABELS = { customer: "Customer", cashier: "Cashier", manager: "Manager" };
// Login page for managing user permissions
// Signed out: username/password/Enter/Back.
// Signed in: a button for each view the user's role allows/Log out/Back.
export default function Login({ user, allowedViews, onLogin, onPick, onLogout, onBack }) {
  const [error, setError] = useState("");

  const submit = async (e) => {
    e.preventDefault();
    const { username, password } = Object.fromEntries(new FormData(e.currentTarget));
    try {
      onLogin(await api("/login", { method: "POST", body: { username, password } }));
    }
    catch (err) {
      setError(err.message || "Login failed");
    }
  };

  if (user) {
    return (
      <div className="h-screen flex flex-col items-center justify-center gap-3">
        <p>Signed in as {user.name} ({user.perms})</p>
        {allowedViews.map((v) => (
          <button key={v} onClick={() => onPick(v)}>{VIEW_LABELS[v]}</button>
        ))}
        <button onClick={onLogout}>Log out</button>
        <button onClick={onBack}>Back</button>
      </div>
    );
  }

  return (
    <form className="h-screen flex flex-col items-center justify-center gap-3" onSubmit={submit}>
      <input name="username" placeholder="Username" />
      <input name="password" type="password" placeholder="Password" />
      <button type="submit">Enter</button>
      <button type="button" onClick={onBack}>Back</button>
      {error && <p>{error}</p>}
    </form>
  );
}
