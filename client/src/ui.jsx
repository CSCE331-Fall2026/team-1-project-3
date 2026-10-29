import { useEffect, useRef } from "react";

// Replaces Alert: an info/error dialog with an OK button.
export function Notice({ notice, onClose }) {
  const ref = useRef(null);
  useEffect(() => ref.current?.showModal(), []);
  return (
    <dialog ref={ref} className="modal" onClose={onClose}>
      <h3>{notice.title}</h3>
      {notice.header && <p><strong>{notice.header}</strong></p>}
      {notice.details && <p>{notice.details}</p>}
      <form method="dialog">
        <button autoFocus>OK</button>
      </form>
    </dialog>
  );
}

// Replaces TextInputDialog / Dialog + GridPane: a titled form with labelled text fields.
export function FormDialog({ title, header, fields, submitLabel = "OK", onSubmit, onCancel }) {
  const ref = useRef(null);
  useEffect(() => ref.current?.showModal(), []);
  return (
    <dialog ref={ref} className="modal" onCancel={onCancel}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit(Object.fromEntries(new FormData(e.currentTarget)));
        }}
      >
        <h3>{title}</h3>
        <p>{header}</p>
        {fields.map((f, i) => (
          <label key={f.name} className="field">
            {f.label}
            <input name={f.name} placeholder={f.placeholder} autoFocus={i === 0} />
          </label>
        ))}
        <div className="actions">
          <button type="button" onClick={onCancel}>Cancel</button>
          <button type="submit">{submitLabel}</button>
        </div>
      </form>
    </dialog>
  );
}
