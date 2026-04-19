package com.leadrush.landingpage.controller;

import com.leadrush.landingpage.entity.LandingPage;
import com.leadrush.landingpage.repository.LandingPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PUBLIC sitemap.xml — discoverable by search engines.
 * Lists static marketing/auth URLs plus every PUBLISHED landing page.
 *
 * Mounted under /api/v1/public/** so it's exempt from auth.
 * The Nuxt SSR server proxies https://app.example.com/sitemap.xml here so the
 * sitemap appears at the canonical site root that crawlers expect.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class SitemapController {

    private final LandingPageRepository pageRepository;

    @Value("${leadrush.frontend-url:http://localhost:4000}")
    private String frontendUrl;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String base = stripTrailingSlash(frontendUrl);
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static high-value URLs.
        appendUrl(xml, base + "/", null, "weekly", "1.0");
        appendUrl(xml, base + "/auth/login", null, "monthly", "0.3");
        appendUrl(xml, base + "/auth/register", null, "monthly", "0.5");

        // Published landing pages — one URL per slug, lastmod from publishedAt.
        List<LandingPage> published = pageRepository.findByStatusOrderByPublishedAtDesc(LandingPage.Status.PUBLISHED);
        for (LandingPage p : published) {
            String loc = base + "/p/" + p.getSlug();
            String lastmod = p.getPublishedAt() != null ? formatDate(p.getPublishedAt()) : null;
            appendUrl(xml, loc, lastmod, "weekly", "0.8");
        }

        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(xml.toString());
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escape(loc)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String formatDate(LocalDateTime dt) {
        return dt.toLocalDate().format(ISO);
    }

    /** Bare-minimum XML escape for URLs — we only ever feed in URLs we control. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
