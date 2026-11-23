import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import "./styles.css";

// oauth doesnt allow localhost for the login redirect, 127.0.0.1 is the same thing and is allowed
if (window.location.hostname === "localhost") {
  window.location.replace(window.location.href.replace("//localhost", "//127.0.0.1"));
}
else {
  createRoot(document.getElementById("root")).render(
    <StrictMode>
      <App />
    </StrictMode>
  );
}
