// Basic login page: username, password, and an Enter button.
export default function Login() {
  return (
    <form
      className="h-screen flex flex-col items-center justify-center gap-3"
      onSubmit={(e) => e.preventDefault()}
    >
      <input name="username" placeholder="Username" />
      <input name="password" type="password" placeholder="Password" />
      <button type="submit">Enter</button>
    </form>
  );
}
