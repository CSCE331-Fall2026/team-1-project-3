import { useEffect, useRef } from "react";

// box-content keeps min-width excluding padding; m-auto re-centres the dialog after preflight
const MODAL = "m-auto box-content min-w-[320px] border border-[grey] rounded-[10px] px-5 py-4 backdrop:bg-black/35";
const MODAL_TITLE = "mb-[1em] text-[1.17em] font-bold";

// Replaces Alert: an info/error dialog with an OK button.
export function Notice({ notice, onClose }) {
  const ref = useRef(null);
  useEffect(() => ref.current?.showModal(), []);
  return (
    <dialog ref={ref} className={MODAL} onClose={onClose}>
      <h3 className={MODAL_TITLE}>{notice.title}</h3>
      {notice.header && <p className="my-4"><strong>{notice.header}</strong></p>}
      {notice.details && <p className="my-4">{notice.details}</p>}
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
    <dialog ref={ref} className={MODAL} onCancel={onCancel}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit(Object.fromEntries(new FormData(e.currentTarget)));
        }}
      >
        <h3 className={MODAL_TITLE}>{title}</h3>
        <p className="my-4">{header}</p>
        {fields.map((f, i) => (
          <label key={f.name} className="grid grid-cols-[110px_1fr] gap-2 items-center mb-2.5">
            {f.label}
            <input name={f.name} placeholder={f.placeholder} autoFocus={i === 0} defaultValue={f.defaultValue ?? ""} />
          </label>
        ))}
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onCancel}>Cancel</button>
          <button type="submit">{submitLabel}</button>
        </div>
      </form>
    </dialog>
  );
}
