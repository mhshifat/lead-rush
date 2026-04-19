import { reactive } from 'vue'

type Variant = 'default' | 'destructive'

interface ConfirmOptions {
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: Variant
}

// Module-level singleton — the shared ConfirmDialog mounted in app.vue subscribes to this.
const state = reactive({
  open: false,
  title: '',
  description: '',
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
  variant: 'default' as Variant,
  resolver: null as ((ok: boolean) => void) | null,
})

function resolveWith(result: boolean) {
  const fn = state.resolver
  state.resolver = null
  state.open = false
  fn?.(result)
}

export function useConfirm() {
  return {
    ask(opts: ConfirmOptions): Promise<boolean> {
      return new Promise((resolve) => {
        state.title = opts.title
        state.description = opts.description ?? ''
        state.confirmLabel = opts.confirmLabel ?? 'Confirm'
        state.cancelLabel = opts.cancelLabel ?? 'Cancel'
        state.variant = opts.variant ?? 'default'
        state.resolver = resolve
        state.open = true
      })
    },
    _state: state,
    _confirm: () => resolveWith(true),
    _cancel: () => resolveWith(false),
  }
}
