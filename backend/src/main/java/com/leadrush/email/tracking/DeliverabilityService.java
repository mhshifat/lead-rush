package com.leadrush.email.tracking;

import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.UUID;

// SPF / DKIM / DMARC DNS checks via JNDI. Results cached for 24h per workspace+domain.
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliverabilityService {

    private static final String DEFAULT_DKIM_SELECTOR = "default";
    private static final long CACHE_TTL_HOURS = 24;

    private final EmailDeliverabilityCheckRepository repository;

    @Transactional
    public EmailDeliverabilityCheck checkDomain(String domain, String dkimSelector) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        var cached = repository.findByWorkspaceIdAndDomain(workspaceId, domain);
        if (cached.isPresent()) {
            var check = cached.get();
            if (check.getCheckedAt() != null
                    && check.getCheckedAt().isAfter(LocalDateTime.now().minusHours(CACHE_TTL_HOURS))) {
                return check;
            }
        }

        EmailDeliverabilityCheck result = cached.orElseGet(() -> {
            EmailDeliverabilityCheck fresh = EmailDeliverabilityCheck.builder()
                    .domain(domain)
                    .build();
            fresh.setWorkspaceId(workspaceId);
            return fresh;
        });

        String spf = lookupTxtStartingWith(domain, "v=spf1");
        result.setSpfRecord(spf);
        result.setSpfStatus(spf != null ? "PASS" : "NOT_FOUND");

        String selector = dkimSelector != null ? dkimSelector : DEFAULT_DKIM_SELECTOR;
        String dkim = lookupTxtStartingWith(selector + "._domainkey." + domain, "v=DKIM1");
        result.setDkimSelector(selector);
        result.setDkimRecord(dkim);
        result.setDkimStatus(dkim != null ? "PASS" : "NOT_FOUND");

        String dmarc = lookupTxtStartingWith("_dmarc." + domain, "v=DMARC1");
        result.setDmarcRecord(dmarc);
        result.setDmarcStatus(dmarc != null ? "PASS" : "NOT_FOUND");

        result.setCheckedAt(LocalDateTime.now());
        return repository.save(result);
    }

    private String lookupTxtStartingWith(String hostname, String prefix) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(hostname, new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");

            if (txt == null) return null;

            for (int i = 0; i < txt.size(); i++) {
                String record = txt.get(i).toString();
                // DNS returns TXT records wrapped in quotes.
                record = record.replace("\"", "");
                if (record.startsWith(prefix)) {
                    return record;
                }
            }
            return null;
        } catch (NamingException e) {
            log.debug("DNS TXT lookup failed for {}: {}", hostname, e.getMessage());
            return null;
        }
    }
}
