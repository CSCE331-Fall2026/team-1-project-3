const VIEW_LABELS = { customer: "Customer", cashier: "Cashier", manager: "Manager", kitchen: "Kitchen" };

// Signed in page after logging in, a button for each view the role allows/Log out/Back.
// The username/password form is on the server (resources/templates/login.html)
export default function SignedIn({ user, allowedViews, onPick, onLogout, onBack }) {
  const viewButtons = [];
  for (const v of allowedViews) {
    viewButtons.push(<button key={v} onClick={() => onPick(v)}>{VIEW_LABELS[v]}</button>);
  }

  return (
    <div className="h-screen flex flex-col items-center justify-center gap-3">
      <p>Signed in as {user.name} ({user.perms})</p>
      {viewButtons}
      <button onClick={onLogout}>Log out</button>
      <button onClick={onBack}>Back</button>
    </div>
  );
}
