package com.splitwise.app.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 2Factor.in-specific config. Isolated under `app.sms.twofactor.*` - deleting
 * this whole file + TwoFactorSmsSender + TwoFactorConfig is the entire
 * footprint of removing this provider later; nothing else in the codebase
 * references 2Factor directly.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sms.twofactor")
public class TwoFactorProperties {

    /**
     * From the 2Factor.in dashboard - keep in env vars/secrets, never commit
     * it.
     */
    private String apiKey;

    /**
     * Optional. 2Factor's "use your own OTP" endpoint (which is what we use,
     * since OtpService already generates and owns the code) works with their
     * default template if this is left blank. Set this only if you've set up a
     * custom approved template on their dashboard and want to use it instead.
     */
    private String templateName;

    private String baseUrl = "https://2factor.in";
}
