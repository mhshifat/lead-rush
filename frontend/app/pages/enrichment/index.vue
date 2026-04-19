<script setup lang="ts">
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Switch } from '~/components/ui/switch'
import { Tooltip, TooltipContent, TooltipTrigger } from '~/components/ui/tooltip'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '~/components/ui/dialog'
import { toast } from 'vue-sonner'
import {
  Database, HardDrive, Github, Map, Globe, Wrench, Target, Users, Building2,
  ChevronUp, ChevronDown, KeyRound, CheckCircle2, AlertCircle, Sparkles,
} from 'lucide-vue-next'
import type { EnrichmentProvider } from '~/composables/useEnrichment'

definePageMeta({
  middleware: 'auth',
})
useHead({ title: 'Enrichment' })

const { data: providers, isLoading } = useEnrichmentProviders()
const updateMutation = useUpdateEnrichmentProvider()

const keyDialogOpen = ref(false)
const editingProvider = ref<EnrichmentProvider | null>(null)
const apiKeyInput = ref('')

function openKeyDialog(provider: EnrichmentProvider) {
  editingProvider.value = provider
  apiKeyInput.value = ''
  keyDialogOpen.value = true
}

async function handleSaveKey() {
  if (!editingProvider.value) return
  try {
    await updateMutation.mutateAsync({
      providerKey: editingProvider.value.providerKey,
      apiKey: apiKeyInput.value,
    })
    toast.success('API key updated')
    keyDialogOpen.value = false
  } catch {
    toast.error('Failed to update API key')
  }
}

async function handleToggleEnabled(provider: EnrichmentProvider) {
  if (!provider.enabled && provider.requiresApiKey && !provider.hasApiKey) {
    toast.error('Set an API key before enabling this provider')
    return
  }
  try {
    await updateMutation.mutateAsync({
      providerKey: provider.providerKey,
      enabled: !provider.enabled,
    })
    toast.success(provider.enabled ? 'Disabled' : 'Enabled')
  } catch {
    toast.error('Failed to update provider')
  }
}

async function handleChangePriority(provider: EnrichmentProvider, direction: 'up' | 'down') {
  const delta = direction === 'up' ? -10 : 10
  try {
    await updateMutation.mutateAsync({
      providerKey: provider.providerKey,
      priority: Math.max(0, provider.priority + delta),
    })
  } catch {
    toast.error('Failed to reorder')
  }
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}

// Each provider knows which tier it belongs to — drives the coloured dot + icon.
const PROVIDER_META: Record<string, { icon: any; tier: 'cache' | 'free' | 'paid'; tagline: string }> = {
  PATTERN_CACHE:       { icon: Database,    tier: 'cache', tagline: 'Learned email patterns per domain' },
  COMPANY_CRAWL_CACHE: { icon: HardDrive,   tier: 'cache', tagline: 'Persons found by the background crawler' },
  GITHUB:              { icon: Github,      tier: 'free',  tagline: 'Developer emails via public commits' },
  SITEMAP_CRAWLER:     { icon: Map,         tier: 'free',  tagline: 'Sitemap + team pages + JSON-LD' },
  WEBSITE_SCRAPER:     { icon: Globe,       tier: 'free',  tagline: 'Company site contact pages' },
  MOCK:                { icon: Wrench,      tier: 'free',  tagline: 'Dev fixture (never real data)' },
  HUNTER:              { icon: Target,      tier: 'paid',  tagline: 'Hunter.io email finder' },
  PDL:                 { icon: Users,       tier: 'paid',  tagline: 'People Data Labs full profile' },
  COMPANIES_HOUSE:     { icon: Building2,   tier: 'paid',  tagline: 'UK registry — director titles' },
}

function metaFor(key: string) {
  return PROVIDER_META[key] ?? { icon: Sparkles, tier: 'free' as const, tagline: '' }
}

// Tier styling — colored dot + matching accent on hover.
function tierClasses(tier: 'cache' | 'free' | 'paid') {
  return {
    cache: 'bg-sky-400',
    free:  'bg-emerald-400',
    paid:  'bg-amber-400',
  }[tier]
}

function tierLabel(tier: 'cache' | 'free' | 'paid') {
  return {
    cache: 'Cache',
    free:  'Free',
    paid:  'Paid',
  }[tier]
}
</script>

<template>
  <div class="space-y-5 enter-fade-up">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-semibold tracking-tight">Enrichment providers</h1>
      <p class="text-sm text-muted-foreground mt-0.5">
        Providers run as a waterfall in priority order (lowest first) until one returns data.
        Cached + free tiers run before paid ones.
      </p>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="glass hairline rounded-xl py-16 text-center text-sm text-muted-foreground">
      Loading…
    </div>

    <!-- Providers list -->
    <div v-else class="glass hairline rounded-xl overflow-hidden">
      <div
        v-for="(p, idx) in providers"
        :key="p.providerKey"
        class="group px-5 py-4 transition-colors hover:bg-white/2"
        :style="idx > 0 ? 'border-top: 1px solid hsl(240 5% 100% / 0.05);' : ''"
      >
        <div class="flex items-center gap-4">
          <!-- Tier dot + icon -->
          <div class="flex items-center gap-3 min-w-0 flex-1">
            <div class="relative shrink-0">
              <div
                class="h-10 w-10 rounded-lg bg-white/5 flex items-center justify-center"
                :class="{ 'ring-1 ring-primary/30': p.enabled }"
              >
                <component :is="metaFor(p.providerKey).icon" class="h-4 w-4 text-muted-foreground" />
              </div>
              <Tooltip>
                <TooltipTrigger>
                  <span
                    class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full"
                    :class="tierClasses(metaFor(p.providerKey).tier)"
                  />
                </TooltipTrigger>
                <TooltipContent>{{ tierLabel(metaFor(p.providerKey).tier) }} tier</TooltipContent>
              </Tooltip>
            </div>

            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <h3 class="font-medium text-sm truncate">{{ p.displayName }}</h3>
                <span class="text-xs uppercase tracking-wider text-muted-foreground font-medium">
                  {{ tierLabel(metaFor(p.providerKey).tier) }}
                </span>
                <span class="text-xs text-muted-foreground tabular-nums">· P{{ p.priority }}</span>
              </div>
              <p class="text-xs text-muted-foreground mt-0.5 truncate">
                {{ metaFor(p.providerKey).tagline }}
              </p>
            </div>
          </div>

          <!-- Stats -->
          <div class="hidden md:flex items-center gap-6 text-xs shrink-0">
            <!-- API key status -->
            <div class="w-24">
              <p class="text-muted-foreground">Key</p>
              <div class="mt-0.5 flex items-center gap-1">
                <template v-if="!p.requiresApiKey">
                  <span class="text-muted-foreground">Not required</span>
                </template>
                <template v-else-if="p.hasApiKey">
                  <CheckCircle2 class="h-3 w-3 text-emerald-400" />
                  <span class="text-emerald-400 font-medium">Set</span>
                </template>
                <template v-else>
                  <AlertCircle class="h-3 w-3 text-destructive" />
                  <span class="text-destructive font-medium">Missing</span>
                </template>
              </div>
            </div>

            <!-- This month -->
            <div class="w-20">
              <p class="text-muted-foreground">This month</p>
              <p class="mt-0.5 font-medium tabular-nums">{{ p.callsThisMonth }}</p>
            </div>

            <!-- Last used -->
            <div class="w-28">
              <p class="text-muted-foreground">Last used</p>
              <p class="mt-0.5 font-medium">{{ formatDate(p.lastUsedAt) }}</p>
            </div>
          </div>

          <!-- Actions — fixed widths so every row's Enable button lines up vertically. -->
          <div class="flex items-center gap-2 shrink-0">
            <!-- Priority chevrons -->
            <div class="flex flex-col rounded-md hairline overflow-hidden">
              <Tooltip>
                <TooltipTrigger>
                  <button
                    class="h-4 w-6 flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-white/5 transition-colors"
                    @click="handleChangePriority(p, 'up')"
                  >
                    <ChevronUp class="h-3 w-3" />
                  </button>
                </TooltipTrigger>
                <TooltipContent>Higher priority</TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger>
                  <button
                    class="h-4 w-6 flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-white/5 transition-colors"
                    @click="handleChangePriority(p, 'down')"
                  >
                    <ChevronDown class="h-3 w-3" />
                  </button>
                </TooltipTrigger>
                <TooltipContent>Lower priority</TooltipContent>
              </Tooltip>
            </div>

            <!-- Key button slot — always 32x32, invisible when provider doesn't need a key. -->
            <Tooltip v-if="p.requiresApiKey">
              <TooltipTrigger>
                <button
                  class="relative h-8 w-8 rounded-md border border-input bg-background hover:bg-white/5 flex items-center justify-center text-muted-foreground hover:text-foreground transition-colors"
                  @click="openKeyDialog(p)"
                >
                  <KeyRound class="h-3.5 w-3.5" />
                  <span
                    v-if="!p.hasApiKey"
                    class="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-destructive"
                  />
                </button>
              </TooltipTrigger>
              <TooltipContent>{{ p.hasApiKey ? 'Change API key' : 'Set API key required' }}</TooltipContent>
            </Tooltip>
            <div v-else class="h-8 w-8" aria-hidden="true" />

            <!-- Enable/disable — switch. -->
            <Tooltip>
              <TooltipTrigger>
                <Switch
                  :model-value="p.enabled"
                  :disabled="updateMutation.isPending.value"
                  @update:model-value="handleToggleEnabled(p)"
                />
              </TooltipTrigger>
              <TooltipContent>{{ p.enabled ? 'Disable provider' : 'Enable provider' }}</TooltipContent>
            </Tooltip>
          </div>
        </div>

        <!-- Last error -->
        <div
          v-if="p.lastError"
          class="mt-3 rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive flex items-start gap-2"
        >
          <AlertCircle class="h-3.5 w-3.5 shrink-0 mt-0.5" />
          <span>{{ p.lastError }}</span>
        </div>
      </div>
    </div>

    <!-- Tier legend -->
    <div class="flex items-center gap-4 text-xs text-muted-foreground px-1">
      <div class="flex items-center gap-1.5">
        <span class="h-2 w-2 rounded-full bg-sky-400" />
        Cache — instant, free
      </div>
      <div class="flex items-center gap-1.5">
        <span class="h-2 w-2 rounded-full bg-emerald-400" />
        Free — scrape or pattern-guess
      </div>
      <div class="flex items-center gap-1.5">
        <span class="h-2 w-2 rounded-full bg-amber-400" />
        Paid — external API
      </div>
    </div>

    <!-- API key dialog -->
    <Dialog v-model:open="keyDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ editingProvider?.displayName }} API key</DialogTitle>
          <DialogDescription>
            The key is AES-256-GCM encrypted before storage. Leave blank to remove the existing key.
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-2">
          <Label for="apiKey">API key</Label>
          <Input id="apiKey" v-model="apiKeyInput" type="password" placeholder="Paste your API key" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="keyDialogOpen = false">Cancel</Button>
          <Button @click="handleSaveKey" :disabled="updateMutation.isPending.value">
            {{ updateMutation.isPending.value ? 'Saving…' : 'Save' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
