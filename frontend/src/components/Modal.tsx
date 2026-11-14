import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { X } from 'lucide-react';

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Close"><X size={16} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function PromptModal(props: {
  title: string;
  label: string;
  initial?: string;
  placeholder?: string;
  submitLabel: string;
  /** Return an error message to keep the dialog open. */
  onSubmit: (value: string) => string | void;
  onClose: () => void;
}) {
  const [value, setValue] = useState(props.initial ?? '');
  const [error, setError] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const problem = props.onSubmit(value);
    if (problem) setError(problem);
    else props.onClose();
  };

  return (
    <Modal title={props.title} onClose={props.onClose}>
      <form onSubmit={submit} className="stack">
        <label className="field">
          <span>{props.label}</span>
          <input autoFocus value={value} placeholder={props.placeholder} onChange={(e) => { setValue(e.target.value); setError(''); }} />
        </label>
        {error && <p className="field-error">{error}</p>}
        <div className="row end">
          <button type="button" className="btn" onClick={props.onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary">{props.submitLabel}</button>
        </div>
      </form>
    </Modal>
  );
}

export function ConfirmModal(props: { title: string; message: string; confirmLabel: string; busy?: boolean; onConfirm: () => void; onClose: () => void }) {
  return (
    <Modal title={props.title} onClose={props.onClose}>
      <div className="stack">
        <p className="muted">{props.message}</p>
        <div className="row end">
          <button className="btn" onClick={props.onClose}>Cancel</button>
          <button className="btn btn-danger" disabled={props.busy} onClick={props.onConfirm}>
            {props.busy && <span className="spinner" />} {props.confirmLabel}
          </button>
        </div>
      </div>
    </Modal>
  );
}
