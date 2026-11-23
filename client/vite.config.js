import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const backend = "http://localhost:3001";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // /oauth2, /login, /logout are the login pages on the spring server
  server: { proxy: { "/api": backend, "/oauth2": backend, "/login": backend, "/logout": backend } },
});
