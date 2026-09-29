import { useRef, useState } from 'react';
import { Plus, RotateCcw } from 'lucide-react';

const initialFields = { userId: '1048', itemName: '', quantity: '1', totalAmount: '' };

// Owns transient draft/validation state and delegates the persisted create action to its parent.
export default function OrderForm({ onCreate, saving }) {
  // React owns these controlled fields; the PO note demonstrates reading a DOM-owned value through a ref.
  const [fields, setFields] = useState(initialFields);
  const [validationError, setValidationError] = useState('');
  const [savedNote, setSavedNote] = useState('');
  const referenceRef = useRef(null);

  function updateField(event) {
    setFields((current) => ({ ...current, [event.target.name]: event.target.value }));
    setValidationError('');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    // Give fast feedback here, but OrderController repeats these constraints because browser input is untrusted.
    const userId = Number(fields.userId);
    const quantity = Number(fields.quantity);
    const totalAmount = Number(fields.totalAmount);
    if (!Number.isInteger(userId) || userId <= 0) return setValidationError('Enter a positive numeric user ID.');
    if (!fields.itemName.trim()) return setValidationError('Add an item name.');
    if (!Number.isInteger(quantity) || quantity <= 0) return setValidationError('Quantity must be a positive whole number.');
    if (!Number.isFinite(totalAmount) || totalAmount <= 0) return setValidationError('Total must be greater than zero.');

    try {
      await onCreate({ userId, itemName: fields.itemName.trim(), quantity, totalAmount });
      setSavedNote(referenceRef.current?.value.trim() ?? '');
      setFields(initialFields);
      if (referenceRef.current) referenceRef.current.value = '';
    } catch {
      return;
    }
  }

  return (
    <section className="form-panel" aria-labelledby="new-order-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">New transaction</p>
          <h2 id="new-order-title">Create an order</h2>
        </div>
        <span className="step-mark">01</span>
      </div>
      <form onSubmit={handleSubmit} noValidate>
        <label className="field-label" htmlFor="userId">Customer ID <span>required</span></label>
        <input id="userId" name="userId" type="number" min="1" step="1" value={fields.userId} onChange={updateField} required />

        <label className="field-label" htmlFor="itemName">Item <span>required</span></label>
        <input id="itemName" name="itemName" value={fields.itemName} onChange={updateField} placeholder="e.g. Field recorder" maxLength="100" required />

        <div className="field-pair">
          <div>
            <label className="field-label" htmlFor="quantity">Quantity</label>
            <input id="quantity" name="quantity" type="number" min="1" step="1" value={fields.quantity} onChange={updateField} required />
          </div>
          <div>
            <label className="field-label" htmlFor="totalAmount">Total <span>USD</span></label>
            <input id="totalAmount" name="totalAmount" type="number" min="0.01" step="0.01" value={fields.totalAmount} onChange={updateField} placeholder="0.00" required />
          </div>
        </div>

        <label className="field-label" htmlFor="reference">PO reference <span>uncontrolled demo</span></label>
        <input id="reference" ref={referenceRef} defaultValue="" placeholder="Read from the DOM on submit" maxLength="40" />

        {validationError && <p className="form-error" role="alert">{validationError}</p>}
        {savedNote && <p className="form-success" role="status">Order created. Local PO note: {savedNote}</p>}
        <p className="field-hint">Payment is requested by Order Service after the order is saved.</p>

        <div className="form-actions">
          <button className="button button-primary" type="submit" disabled={saving}>
            <Plus size={17} aria-hidden="true" /> {saving ? 'Submitting…' : 'Create order'}
          </button>
          <button className="icon-button" type="button" title="Reset form" aria-label="Reset form" onClick={() => { setFields(initialFields); setValidationError(''); setSavedNote(''); if (referenceRef.current) referenceRef.current.value = ''; }}>
            <RotateCcw size={16} aria-hidden="true" />
          </button>
        </div>
      </form>
    </section>
  );
}