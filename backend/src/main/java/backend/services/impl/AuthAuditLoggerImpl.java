package backend.services.impl;

import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.services.intf.AuthAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthAuditLoggerImpl implements AuthAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void log(Event event, String subject, String detail) {
        try {
            ClientInfo info = ClientRequestContext.get();
            String ip = info != null ? info.ip() : "unknown";
            String userAgent = info != null ? info.userAgent() : "";
            AUDIT.info("event={} subject={} ip={} ua=\"{}\" detail={}",
                    event,
                    subject == null ? "-" : subject,
                    ip,
                    sanitize(userAgent),
                    detail == null ? "-" : sanitize(detail));
        } catch (Exception ex) {
            // Audit logging must never break the wrapped operation. Demote to a warn
            // on the standard logger and keep going.
            LoggerFactory.getLogger(AuthAuditLoggerImpl.class)
                    .warn("Failed to record auth audit event {}: {}", event, ex.getMessage());
        }
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        // Strip newlines so an attacker cannot forge log lines via a crafted User-Agent.
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
