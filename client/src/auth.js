// OAuth2 login with our own server (sessions managed by springboot security)
// startLogin sends you to the server's login page, the server sends you back with ?code=...,
// then finishLogin trades the code for a token that api() sends on every request to verify

const CLIENT_ID = "pos-web";
const SCOPE = "pos";
const TOKEN_KEY = "pos.accessToken";
const PKCE_KEY = "pos.pkce";

function readStore(key) {
  try {
    return sessionStorage.getItem(key);
  }
  catch {
    return null;
  }
}

function writeStore(key, value) {
  try {
    sessionStorage.setItem(key, value);
  }
  catch {
  }
}

function removeStore(key) {
  try {
    sessionStorage.removeItem(key);
  }
  catch {
  }
}

function redirectUri() {
  return `${window.location.origin}/`;
}

function base64url(bytes) {
  const text = String.fromCharCode(...new Uint8Array(bytes));
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomString() {
  return base64url(crypto.getRandomValues(new Uint8Array(32)));
}

// reads the json in the middle of the token, the server checks the signature to confirm it matches (cant just say "im manager")
function readClaims(token) {
  try {
    const part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(part));
  }
  catch {
    return null;
  }
}

// the saved token, or null if there isnt one or it expired
export function accessToken() {
  const token = readStore(TOKEN_KEY);
  if (!token) {
    return null;
  }
  const claims = readClaims(token);
  if (!claims || claims.exp * 1000 <= Date.now()) {
    removeStore(TOKEN_KEY);
    return null;
  }
  return token;
}

export function currentUser() {
  const token = accessToken();
  if (!token) {
    return null;
  }
  const claims = readClaims(token);
  return { userId: claims.user_id, name: claims.name, perms: claims.perms };
}

export async function startLogin() {
  const verifier = randomString();
  const state = randomString();
  writeStore(PKCE_KEY, JSON.stringify({ verifier, state }));

  const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  const params = new URLSearchParams({ response_type: "code", client_id: CLIENT_ID, redirect_uri: redirectUri(), scope: SCOPE });
  params.set("state", state);
  params.set("code_challenge", base64url(hash));
  params.set("code_challenge_method", "S256");
  window.location.assign(`/oauth2/authorize?${params}`);
}

// returns the user if we just came back from the login page, otherwise null
export async function finishLogin() {
  const query = new URLSearchParams(window.location.search);
  const code = query.get("code");
  if (!code && !query.has("error")) {
    return null;
  }

  window.history.replaceState(null, "", "/");
  const saved = JSON.parse(readStore(PKCE_KEY) ?? "null");
  removeStore(PKCE_KEY);
  if (!code || !saved || saved.state !== query.get("state")) {
    return null;
  }

  const form = new URLSearchParams({ grant_type: "authorization_code", code, redirect_uri: redirectUri() });
  form.set("client_id", CLIENT_ID);
  form.set("code_verifier", saved.verifier);
  const res = await fetch("/oauth2/token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: form });
  if (!res.ok) {
    return null;
  }
  const data = await res.json();
  writeStore(TOKEN_KEY, data.access_token);
  return currentUser();
}

export function logout() {
  removeStore(TOKEN_KEY);
  window.location.assign("/logout");
}
