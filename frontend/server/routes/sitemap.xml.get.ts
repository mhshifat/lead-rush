// Proxies /sitemap.xml on the site domain to the backend's public sitemap endpoint.
// Crawlers expect the sitemap at the site root, not under /api/v1/public/.
// We serve it from the SSR layer so the URL is stable regardless of how the API
// is exposed (subdomain, path-prefix, internal-only).

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig()
  const apiBase = config.apiBaseUrlServer || config.public.apiBaseUrl

  try {
    const xml = await $fetch<string>(`${apiBase}/public/sitemap.xml`, {
      responseType: 'text',
    })
    setHeader(event, 'content-type', 'application/xml; charset=utf-8')
    setHeader(event, 'cache-control', 'public, max-age=3600')
    return xml
  } catch (err) {
    // Fail open with a tiny stub — better than 500ing the crawler.
    setHeader(event, 'content-type', 'application/xml; charset=utf-8')
    return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>${config.public.siteUrl || ''}/</loc></url>
</urlset>`
  }
})
