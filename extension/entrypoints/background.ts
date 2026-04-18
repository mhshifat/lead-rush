/**
 * Background service worker — the ONLY place that calls the Lead Rush API.
 *
 * Why centralize here?
 *   - Chrome MV3 gives service workers host_permissions, bypassing CORS
 *   - Content scripts injected into linkedin.com can't fetch leadrush.com without CORS
 *   - Popup + content scripts just send typed messages; we handle the HTTP
 */
import { defineBackground } from 'wxt/sandbox'
import { apiFetch, ApiError } from '@/lib/api'
import { clearConfig, getConfig, setConfig } from '@/lib/storage'
import type {
  ExtensionTaskDto,
  LinkedInImportResult,
  MeDto,
  Message,
  MessageResponse,
} from '@/lib/types'

export default defineBackground(() => {
  chrome.runtime.onMessage.addListener((raw, _sender, sendResponse) => {
    const message = raw as Message

    // Promise must be resolved via sendResponse — return true to keep the channel open.
    handle(message)
      .then(data => sendResponse({ ok: true, data } satisfies MessageResponse))
      .catch((err: unknown) => {
        const errorMessage = err instanceof ApiError || err instanceof Error
          ? err.message
          : 'Unknown error'
        sendResponse({ ok: false, error: errorMessage } satisfies MessageResponse)
      })

    return true // async response
  })
})

// ── Dispatcher ──

async function handle(message: Message): Promise<unknown> {
  switch (message.type) {
    case 'setConfig': {
      await setConfig({ apiKey: message.apiKey, apiBaseUrl: message.apiBaseUrl })
      return await fetchMe()
    }
    case 'getConfig':
      return await getConfig()
    case 'clearConfig':
      await clearConfig()
      return null
    case 'me':
      return await fetchMe()
    case 'listTasks':
      return await listTasks()
    case 'tasksForUrl':
      return await tasksForUrl(message.url)
    case 'completeTask':
      return await completeTask(message.id)
    case 'importContact':
      return await importContact(message.payload)
  }
}

// ── API calls ──

async function fetchMe(): Promise<MeDto> {
  const config = await requireConfig()
  return apiFetch<MeDto>(config, '/api/v1/ext/me')
}

async function listTasks(): Promise<ExtensionTaskDto[]> {
  const config = await requireConfig()
  return apiFetch<ExtensionTaskDto[]>(config, '/api/v1/ext/tasks')
}

async function tasksForUrl(url: string): Promise<ExtensionTaskDto[]> {
  const config = await requireConfig()
  const qs = new URLSearchParams({ url }).toString()
  return apiFetch<ExtensionTaskDto[]>(config, `/api/v1/ext/tasks/by-linkedin-url?${qs}`)
}

async function completeTask(id: string): Promise<void> {
  const config = await requireConfig()
  await apiFetch<void>(config, `/api/v1/ext/tasks/${id}/complete`, { method: 'POST' })
}

async function importContact(payload: unknown): Promise<LinkedInImportResult> {
  const config = await requireConfig()
  return apiFetch<LinkedInImportResult>(config, '/api/v1/ext/contacts/from-linkedin', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function requireConfig() {
  const config = await getConfig()
  if (!config) throw new Error('Not connected — paste your API key in the extension popup')
  return config
}
