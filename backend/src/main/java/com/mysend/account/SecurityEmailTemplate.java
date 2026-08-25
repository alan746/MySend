package com.mysend.account;

final class SecurityEmailTemplate {

    private static final String PRODUCT_URL_LABEL = "Open MySend";

    private SecurityEmailTemplate() {
    }

    static Message accountVerification(String code, String appBaseUrl) {
        return build(
                new Copy(
                        "Verify your MySend email",
                        "Your MySend verification code expires in 10 minutes.",
                        "ACCOUNT VERIFICATION",
                        "Finish setting up your account.",
                        "Use this six-digit code to verify your email address. "
                                + "It works once and expires quickly.",
                        "If you did not create a MySend account, you can safely "
                                + "ignore this email."
                ),
                code,
                appBaseUrl
        );
    }

    static Message passwordChange(String code, String appBaseUrl) {
        return build(
                new Copy(
                        "Reset your MySend password",
                        "Your MySend password code expires in 10 minutes.",
                        "PASSWORD SECURITY",
                        "Confirm your password change.",
                        "Use this six-digit code to continue. It works once and "
                                + "expires quickly.",
                        "If you did not request a password change, ignore this "
                                + "email and your password will stay the same."
                ),
                code,
                appBaseUrl
        );
    }

    private static Message build(Copy copy, String code, String appBaseUrl) {
        String safeCode = escapeHtml(code);
        String normalizedUrl = normalizeUrl(appBaseUrl);
        String safeUrl = escapeHtml(normalizedUrl);
        String text = """
                MySend

                %s

                Your six-digit code:
                %s

                This code expires in 10 minutes and can be used once.
                %s

                %s: %s
                """.formatted(
                copy.heading(),
                code,
                copy.securityNote(),
                PRODUCT_URL_LABEL,
                normalizedUrl
        );
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="color-scheme" content="light only">
                  <title>%s</title>
                  <style>
                    @media only screen and (max-width: 620px) {
                      .email-shell { width: 100%% !important; }
                      .email-pad { padding-left: 24px !important; padding-right: 24px !important; }
                      .email-heading { font-size: 34px !important; line-height: 38px !important; }
                      .email-code { font-size: 38px !important; letter-spacing: 7px !important; }
                    }
                  </style>
                </head>
                <body style="margin:0; padding:0; background:#f1eee3; color:#161b13;">
                  <div style="display:none; max-height:0; overflow:hidden; opacity:0; color:transparent;">
                    %s
                  </div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"
                         style="width:100%%; background:#f1eee3; border-collapse:collapse;">
                    <tr>
                      <td align="center" style="padding:40px 16px;">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"
                               class="email-shell"
                               style="width:600px; max-width:600px; background:#fffdf5; border:1px solid #252b20; border-collapse:collapse;">
                          <tr>
                            <td class="email-pad" style="padding:24px 40px; background:#151a12;">
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0"
                                     style="border-collapse:collapse;">
                                <tr>
                                  <td width="20" height="20" style="width:20px; height:20px; background:#caff3d; border:1px solid #f8f5e9;">&nbsp;</td>
                                  <td style="padding-left:12px; color:#fffdf5; font-family:Arial, Helvetica, sans-serif; font-size:22px; font-weight:800; letter-spacing:-0.5px;">
                                    MySend
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-pad" style="padding:46px 40px 18px;">
                              <div style="color:#708b1d; font-family:Arial, Helvetica, sans-serif; font-size:11px; font-weight:800; letter-spacing:1.8px;">
                                %s
                              </div>
                              <h1 class="email-heading" style="margin:14px 0 16px; color:#161b13; font-family:Arial, Helvetica, sans-serif; font-size:42px; line-height:46px; font-weight:800; letter-spacing:-1.7px;">
                                %s
                              </h1>
                              <p style="margin:0; color:#596052; font-family:Arial, Helvetica, sans-serif; font-size:16px; line-height:25px;">
                                %s
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-pad" style="padding:22px 40px 0;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"
                                     style="width:100%%; background:#f1eee3; border:1px solid #252b20; border-top:8px solid #caff3d; border-collapse:collapse;">
                                <tr>
                                  <td align="center" style="padding:30px 16px 28px;">
                                    <div style="margin-bottom:12px; color:#676d60; font-family:Arial, Helvetica, sans-serif; font-size:10px; font-weight:800; letter-spacing:1.8px;">
                                      YOUR CODE
                                    </div>
                                    <div class="email-code" style="color:#161b13; font-family:'Courier New', Courier, monospace; font-size:46px; line-height:52px; font-weight:700; letter-spacing:11px; white-space:nowrap;">
                                      %s
                                    </div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-pad" style="padding:18px 40px 0;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"
                                     style="width:100%%; background:#151a12; border-collapse:collapse;">
                                <tr>
                                  <td style="padding:18px 20px; color:#fffdf5; font-family:Arial, Helvetica, sans-serif; font-size:14px; line-height:21px;">
                                    <strong style="color:#caff3d;">10 minutes.</strong>
                                    This code can be used once, then it is retired.
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-pad" style="padding:28px 40px 0;">
                              <a href="%s" style="display:inline-block; padding:14px 20px; background:#caff3d; border:1px solid #252b20; color:#161b13; font-family:Arial, Helvetica, sans-serif; font-size:14px; font-weight:800; text-decoration:none;">
                                %s &rarr;
                              </a>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-pad" style="padding:30px 40px 42px;">
                              <p style="margin:0; padding-top:22px; border-top:1px solid #d8d4c8; color:#676d60; font-family:Arial, Helvetica, sans-serif; font-size:13px; line-height:21px;">
                                %s
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-pad" style="padding:18px 40px; background:#e7e3d7; color:#777d70; font-family:Arial, Helvetica, sans-serif; font-size:11px; line-height:18px;">
                              Automated security message from MySend.<br>
                              We will never ask you to reply with this code.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(copy.subject()),
                escapeHtml(copy.preheader()),
                escapeHtml(copy.eyebrow()),
                escapeHtml(copy.heading()),
                escapeHtml(copy.introduction()),
                safeCode,
                safeUrl,
                PRODUCT_URL_LABEL,
                escapeHtml(copy.securityNote())
        );

        return new Message(copy.subject(), text, html);
    }

    private static String normalizeUrl(String appBaseUrl) {
        if (appBaseUrl.endsWith("/")) {
            return appBaseUrl.substring(0, appBaseUrl.length() - 1);
        }
        return appBaseUrl;
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

    private record Copy(
            String subject,
            String preheader,
            String eyebrow,
            String heading,
            String introduction,
            String securityNote
    ) {
    }
}
