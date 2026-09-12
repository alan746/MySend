package com.mysend.account;

final class SecurityEmailTemplate {

    private SecurityEmailTemplate() {
    }

    static Message accountVerification(String code) {
        return build(
                "Your MySend verification code: " + code,
                "Your verification code is:",
                code,
                "This code expires in 10 minutes.",
                "If you did not create a MySend account, you can ignore this email."
        );
    }

    static Message passwordChange(String code) {
        return build(
                "Your MySend password code: " + code,
                "Your password code is:",
                code,
                "This code expires in 10 minutes.",
                "If you did not request a password change, you can ignore this email."
        );
    }

    private static Message build(
            String subject,
            String heading,
            String code,
            String expiry,
            String securityNote
    ) {
        String text = """
                %s

                %s

                %s

                %s
                """.formatted(heading, code, expiry, securityNote);
        String html = """
                <!doctype html>
                <html lang="en">
                <body style="margin:0; padding:24px; background:#ffffff; color:#161b13; font-family:Arial, Helvetica, sans-serif;">
                  <main style="max-width:520px; margin:0 auto;">
                    <p style="margin:0 0 16px; font-size:16px; line-height:24px;">%s</p>
                    <p style="margin:0 0 16px; font-family:'Courier New', Courier, monospace; font-size:32px; line-height:40px; font-weight:700; letter-spacing:6px;">%s</p>
                    <p style="margin:0 0 8px; font-size:14px; line-height:22px;">%s</p>
                    <p style="margin:0; color:#596052; font-size:14px; line-height:22px;">%s</p>
                  </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(heading),
                escapeHtml(code),
                escapeHtml(expiry),
                escapeHtml(securityNote)
        );

        return new Message(subject, text, html);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    record Message(String subject, String text, String html) {
    }
}
