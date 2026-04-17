---
name: form-handling
description: Frontend form handling patterns
category: frontend
---

# Skill: Form Handling

Patterns for form implementation in Next.js applications.

Prerequisite: read `platform/coding-rules.md` (TypeScript Rules section) before using this skill.

---

## Basic Form Pattern

Use React `useState` for form fields. Validate before submit.

```typescript
export function ProductForm({ product, onSubmit }: Props) {
  const [name, setName] = useState(product?.name ?? '');
  const [price, setPrice] = useState(product?.price ?? 0);
  const [error, setError] = useState('');

  const isValid = name.trim().length > 0 && price > 0;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid) return;

    setError('');
    try {
      await onSubmit({ name: name.trim(), price });
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to save'));
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <input value={name} onChange={(e) => setName(e.target.value)} />
      <input type="number" value={price} onChange={(e) => setPrice(Number(e.target.value))} />
      {error && <p className="text-red-500">{error}</p>}
      <button type="submit" disabled={!isValid}>Save</button>
    </form>
  );
}
```

---

## Form with Mutation Hook

Connect forms to mutation hooks for API submission.

```typescript
export function CreateProductPage() {
  const router = useRouter();
  const createProduct = useCreateProduct();

  async function handleSubmit(data: CreateProductRequest) {
    const result = await createProduct.mutateAsync(data);
    router.push(`/products/${result.id}`);
  }

  return <ProductForm onSubmit={handleSubmit} />;
}
```

---

## Validation Patterns

### Required Fields

```typescript
const isValid = name.trim().length > 0 && price > 0;
```

### Format Validation

```typescript
const isEmailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
const isPhoneValid = /^01[0-9]-\d{3,4}-\d{4}$/.test(phone);
```

### Shared Validation

For reusable validation logic, use shared utilities:

```typescript
import { validatePhone } from '@/shared/lib/validation';
```

---

## Edit Form Pattern

Pre-populate from existing data. Track dirty state.

```typescript
export function EditProductForm({ product }: { product: ProductDetail }) {
  const [name, setName] = useState(product.name);
  const [price, setPrice] = useState(product.price);
  const updateProduct = useUpdateProduct();

  const isDirty = name !== product.name || price !== product.price;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isDirty) return;
    await updateProduct.mutateAsync({ id: product.id, name, price });
  }

  return (
    <form onSubmit={handleSubmit}>
      {/* fields */}
      <button type="submit" disabled={!isDirty || updateProduct.isPending}>
        {updateProduct.isPending ? 'Saving...' : 'Save'}
      </button>
    </form>
  );
}
```

---

## Submit State

Disable the submit button during submission. Show loading indicator.

```typescript
<button type="submit" disabled={mutation.isPending}>
  {mutation.isPending ? 'Saving...' : 'Save'}
</button>
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| No `e.preventDefault()` | Form reloads the page without it |
| Submit button enabled during loading | Disable with `mutation.isPending` |
| Error state not cleared on retry | Reset `setError('')` before each submission |
| Uncontrolled inputs mixed with controlled | Use either controlled (`value` + `onChange`) or uncontrolled — not both |
| Missing client-side validation | Validate before calling the API to avoid unnecessary requests |
