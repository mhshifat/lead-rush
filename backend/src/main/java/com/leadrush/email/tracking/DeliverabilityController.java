package com.leadrush.email.tracking;

import com.leadrush.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deliverability")
@RequiredArgsConstructor
public class DeliverabilityController {

    private final DeliverabilityService deliverabilityService;

    /**
     * GET /api/v1/deliverability/check?domain=acme.com&dkimSelector=default
     *
     * Returns SPF/DKIM/DMARC status for a domain.
     * Results are cached for 24 hours.
     */
    @GetMapping("/check")
    public ApiResponse<EmailDeliverabilityCheck> check(
            @RequestParam String domain,
            @RequestParam(required = false) String dkimSelector
    ) {
        return ApiResponse.success(deliverabilityService.checkDomain(domain, dkimSelector));
    }
}
