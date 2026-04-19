import { reactive } from 'vue'

// Tiny reactive error bag for forms. Replaces ad-hoc `toast.error('Name required')`
// calls with field-scoped inline errors — toasts fired from inside dialogs often
// get buried behind the backdrop and the user never sees them.
//
// Usage:
//   const errors = useFieldErrors()
//   errors.set('name', 'Name is required')        ← show next to the Name field
//   errors.set('_form', 'Server rejected this')   ← top-of-form banner
//   errors.clear()                                ← on submit or dialog close
//   errors.remove('name')                         ← when the user fixes one field
//   errors.has('name')                            ← boolean, for input classes
//   errors.get('name')                            ← the current message
export interface FormErrors {
  readonly map: Record<string, string>
  has(field: string): boolean
  get(field: string): string | undefined
  set(field: string, message: string): void
  setAll(errors: Record<string, string>): void
  remove(field: string): void
  clear(): void
  // Absorb a server error response. Knows about the project's ErrorApiDto shape —
  // { error: { category: 'VALIDATION', details: [{ field, message }] } }.
  fromServerError(err: any, fallback?: string): void
}

export function useFieldErrors(): FormErrors {
  const map = reactive<Record<string, string>>({})

  return {
    get map() { return map },
    has: (field) => !!map[field],
    get: (field) => map[field],
    set: (field, message) => { map[field] = message },
    setAll: (errors) => {
      for (const k of Object.keys(map)) delete map[k]
      for (const [k, v] of Object.entries(errors)) map[k] = v
    },
    remove: (field) => { delete map[field] },
    clear: () => { for (const k of Object.keys(map)) delete map[k] },
    fromServerError(err: any, fallback = 'Something went wrong') {
      const data = err?.data?.error
      if (data?.category === 'VALIDATION' && Array.isArray(data.details)) {
        for (const d of data.details) {
          if (d?.field && d?.message) map[d.field] = d.message
        }
        return
      }
      map._form = data?.message ?? fallback
    },
  }
}
