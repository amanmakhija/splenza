import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy — Splenza",
  description: "How Splenza collects, uses, and protects your information.",
};

export default function PrivacyPolicyPage() {
  return (
    <div style={styles.container}>
      <h1 style={styles.h1}>Privacy Policy — Splenza</h1>
      <p style={styles.updated}>
        Last updated: <strong>15/08/2026</strong>
      </p>

      <p style={styles.p}>
        This Privacy Policy explains how Splenza (&quot;we&quot;,
        &quot;our&quot;, &quot;the App&quot;) collects, uses, stores, and
        protects your information when you use our mobile application. By using
        Splenza, you agree to the practices described in this policy.
      </p>
      <p style={styles.p}>
        If you have questions, contact us at:{" "}
        <a href="mailto:help@splenza.in" style={styles.a}>
          help@splenza.in
        </a>
      </p>

      <h2 style={styles.h2}>1. Information We Collect</h2>

      <p style={styles.p}>
        <strong>1.1 Information you provide directly</strong>
      </p>
      <table style={styles.table}>
        <tbody>
          <tr>
            <th style={styles.th}>Data</th>
            <th style={styles.th}>Purpose</th>
            <th style={styles.th}>Required?</th>
          </tr>
          <tr>
            <td style={styles.td}>Name</td>
            <td style={styles.td}>
              Displayed to friends/group members you split expenses with
            </td>
            <td style={styles.td}>Yes</td>
          </tr>
          <tr>
            <td style={styles.td}>Email address</td>
            <td style={styles.td}>
              Account login, verification, password reset, notifications
            </td>
            <td style={styles.td}>
              Yes, if signing up with email; otherwise optional
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Phone number</td>
            <td style={styles.td}>
              Account login and verification (if you sign up or log in with your
              phone number), and lets friends find you by phone number
            </td>
            <td style={styles.td}>
              Yes, if signing up with a phone number; otherwise optional
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Password</td>
            <td style={styles.td}>
              Account authentication (stored as a one-way bcrypt hash — we never
              store or can view your plain-text password)
            </td>
            <td style={styles.td}>
              Only if you sign up with email, or later choose to add a password
              to a phone-based account
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Profile picture</td>
            <td style={styles.td}>Displayed to friends/group members</td>
            <td style={styles.td}>No</td>
          </tr>
          <tr>
            <td style={styles.td}>
              Expense, group, friend, and settlement data
            </td>
            <td style={styles.td}>
              Core app functionality — recording who paid what, group
              memberships, and money owed/settled between users
            </td>
            <td style={styles.td}>Yes, to use the app</td>
          </tr>
          <tr>
            <td style={styles.td}>Receipt photos</td>
            <td style={styles.td}>
              Optional AI-assisted receipt scanning — see section 1.6
            </td>
            <td style={styles.td}>No</td>
          </tr>
        </tbody>
      </table>

      <p style={styles.p}>
        <strong>Verifying your email or phone number.</strong> When you sign up,
        log in, or add a new email address or phone number to your account, we
        send a one-time verification code — by email or SMS, depending on which
        you&apos;re verifying — to confirm you own it. To deliver SMS
        verification codes, your phone number is shared with{" "}
        <strong>2Factor</strong>, our SMS delivery provider, solely for that
        purpose — see section 4 below for more on this and our other third-party
        services.
      </p>
      <p style={styles.p}>
        You can sign up and log in using an email address, a phone number, or
        Google Sign-In. You can also add and verify a second one later from your
        account settings — for example, verifying an email address after signing
        up with your phone number — and, once you have a verified email on file,
        choose to set a password so you can log in that way too.
      </p>

      <p style={styles.p}>
        <strong>1.2 Information from your contacts (optional)</strong>
      </p>
      <p style={styles.p}>
        Splenza includes an optional &quot;Find friends&quot; feature. If you
        choose to grant contacts permission and use this feature, we read the
        phone numbers and email addresses in your device&apos;s contacts and
        send them to our servers to check which of your contacts already have a
        Splenza account, so we can show you an option to add them as a friend.
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          This is entirely optional. You can decline the permission, or decline
          it later from your device settings, and still use every other part of
          the App normally.
        </li>
        <li style={styles.li}>
          We do not store the contact list submitted for this check — it is used
          only to compute matches at that moment and is not retained by our
          servers afterward.
        </li>
        <li style={styles.li}>
          We never message, notify, or contact anyone on your behalf
          automatically. If you choose to invite a contact who isn&apos;t yet on
          Splenza, that invitation is sent through your own device&apos;s
          messaging or sharing apps (e.g. SMS, WhatsApp, email) that you select
          — Splenza itself never sends anything to your contacts directly.
        </li>
        <li style={styles.li}>
          You can revoke contacts access at any time from your device&apos;s
          system settings.
        </li>
      </ul>

      <p style={styles.p}>
        <strong>1.3 Information collected automatically</strong>
      </p>
      <table style={styles.table}>
        <tbody>
          <tr>
            <th style={styles.th}>Data</th>
            <th style={styles.th}>Purpose</th>
          </tr>
          <tr>
            <td style={styles.td}>
              Device push notification token (FCM token)
            </td>
            <td style={styles.td}>
              Delivering notifications (friend requests, expense updates,
              settlements) to your device
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Device platform (Android/iOS)</td>
            <td style={styles.td}>
              Sending platform-appropriate push notifications
            </td>
          </tr>
          <tr>
            <td style={styles.td}>
              App activity log (e.g. &quot;expense created,&quot; &quot;group
              joined&quot;)
            </td>
            <td style={styles.td}>
              Powering the in-app activity feed within your groups
            </td>
          </tr>
        </tbody>
      </table>

      <p style={styles.p}>
        <strong>1.4 Information from third-party sign-in</strong>
      </p>
      <p style={styles.p}>
        If you choose to sign in with Google, we receive your Google
        account&apos;s name, email address, and profile picture from Google, per
        Google&apos;s own privacy practices, to create or log into your Splenza
        account. We do not receive your Google password.
      </p>

      <p style={styles.p}>
        <strong>1.5 Information we do not collect</strong>
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          We do not collect precise or continuous location data.
        </li>
        <li style={styles.li}>
          We do not access your contacts unless you explicitly grant permission
          and choose to use the optional &quot;Find friends&quot; feature
          described in section 1.2.
        </li>
        <li style={styles.li}>
          We do not read your device&apos;s SMS, call logs, or photos beyond an
          image you explicitly choose to upload as a profile picture or receipt.
        </li>
        <li style={styles.li}>
          We do not use advertising identifiers or serve third-party ads.
        </li>
      </ul>

      <p style={styles.p}>
        <strong>
          1.6 Receipt photos and expense descriptions (AI-assisted features)
        </strong>
      </p>
      <p style={styles.p}>
        Splenza includes optional AI-assisted features to help you enter
        expenses faster:
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          <strong>Receipt scanning.</strong> If you choose to photograph or
          select a receipt image, that image is uploaded to our servers and sent
          to <strong>Anthropic</strong>, our AI processing partner, solely to
          read the merchant name, amount, date, and a suggested category from
          it. The extracted details are shown to you to review and edit before
          you save the expense — nothing is saved automatically without your
          confirmation.
        </li>
        <li style={styles.li}>
          <strong>Category suggestion.</strong> If you type a description for an
          expense, that text may also be sent to Anthropic to suggest a matching
          category from your existing categories. This is a free, unlimited
          feature and does not involve any image data.
        </li>
        <li style={styles.li}>
          These features are entirely optional — you can add expenses manually
          without using either of them.
        </li>
        <li style={styles.li}>
          Anthropic processes this data on our behalf to provide these features
          and does not use it to train its models. See section 6 for how long we
          retain receipt images.
        </li>
      </ul>

      <p style={styles.p}>
        <strong>1.7 Microphone permission</strong>
      </p>
      <p style={styles.p}>
        The Android version of the App requests microphone permission. This is
        included in preparation for an upcoming voice-based expense entry
        feature that is <strong>not yet active</strong>. At present, the App
        does not record, access, or transmit any audio, and no microphone data
        is collected. Once voice entry is released, we will update this Privacy
        Policy beforehand to describe exactly what audio data is collected and
        how it&apos;s used.
      </p>

      <p style={styles.p}>
        <strong>1.8 In-app purchases</strong>
      </p>
      <p style={styles.p}>
        Splenza offers optional credit packs (used to unlock extra AI-assisted
        scans beyond your free daily allowance) via Google Play Billing. When
        you make a purchase, Google shares confirmation of the transaction (the
        product purchased and a purchase token) with us so we can credit your
        account — we do not receive or store your card number, bank details, or
        other payment credentials. Your payment method itself is held and
        processed entirely by Google; see Google Play&apos;s own privacy policy
        for how they handle it.
      </p>

      <h2 style={styles.h2}>2. How We Use Your Information</h2>
      <p style={styles.p}>We use the information above to:</p>
      <ul style={styles.ul}>
        <li style={styles.li}>Create and manage your account</li>
        <li style={styles.li}>
          Authenticate you and keep your session secure (via access/refresh
          tokens)
        </li>
        <li style={styles.li}>
          Verify an email address or phone number you sign up, log in, or add to
          your account with, via a one-time code
        </li>
        <li style={styles.li}>
          Let you split expenses, create groups, add friends, and settle
          balances with other users
        </li>
        <li style={styles.li}>
          Show you which of your device contacts already use Splenza, if you
          choose to use the optional &quot;Find friends&quot; feature
        </li>
        <li style={styles.li}>
          Read receipt photos and typed descriptions to auto-fill or suggest
          expense details, if you choose to use these optional AI-assisted
          features
        </li>
        <li style={styles.li}>
          Process optional in-app credit purchases via Google Play Billing and
          maintain your credit balance
        </li>
        <li style={styles.li}>
          Send you transactional push notifications (friend requests, expense
          activity, settlements) and account-related emails or SMS (OTP
          verification, password reset)
        </li>
        <li style={styles.li}>
          Maintain an activity log within groups you belong to, visible to other
          members of that group
        </li>
        <li style={styles.li}>
          Enforce fair use of the service (e.g. rate-limiting abusive request
          patterns, including limiting how often verification codes can be
          requested)
        </li>
        <li style={styles.li}>Diagnose and fix technical problems</li>
      </ul>
      <p style={styles.p}>
        We do <strong>not</strong> sell your personal information to third
        parties, and we do not use your data for targeted advertising.
      </p>

      <h2 style={styles.h2}>3. Who Can See Your Information</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          <strong>Other users in your groups</strong> can see your name, profile
          picture, and the expenses/settlements you&apos;re involved in within
          shared groups — this is core to how the app functions.
        </li>
        <li style={styles.li}>
          <strong>Friends you&apos;ve connected with</strong> can see your name,
          email, and (if provided) phone number, and your shared balance/expense
          history with them.
        </li>
        <li style={styles.li}>
          <strong>We (the Splenza team)</strong> can access account and
          financial-record data as needed to operate, maintain, and troubleshoot
          the service. We do not read your data for any purpose beyond operating
          the app or complying with law.
        </li>
      </ul>
      <p style={styles.p}>
        Your device contacts (phone numbers/emails) submitted through the
        &quot;Find friends&quot; feature are not shown to any other user — they
        are only used server-side to check for matching Splenza accounts.
        Receipt photos and typed descriptions sent for AI processing (section
        1.6) are not visible to other users beyond the expense details you
        choose to save into a shared group, same as if you&apos;d entered them
        manually.
      </p>

      <h2 style={styles.h2}>4. Third-Party Services We Use</h2>
      <table style={styles.table}>
        <tbody>
          <tr>
            <th style={styles.th}>Service</th>
            <th style={styles.th}>Purpose</th>
            <th style={styles.th}>Data shared</th>
          </tr>
          <tr>
            <td style={styles.td}>Google Sign-In</td>
            <td style={styles.td}>Optional authentication method</td>
            <td style={styles.td}>
              Google ID, name, email, profile picture (only if you use this
              sign-in method)
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Firebase Cloud Messaging</td>
            <td style={styles.td}>Delivering push notifications</td>
            <td style={styles.td}>Device push token</td>
          </tr>
          <tr>
            <td style={styles.td}>Firebase Crashlytics</td>
            <td style={styles.td}>Crash reporting to help us fix bugs</td>
            <td style={styles.td}>
              Device/app diagnostic data, not your personal expense data
            </td>
          </tr>
          <tr>
            <td style={styles.td}>2Factor</td>
            <td style={styles.td}>Delivering SMS verification codes</td>
            <td style={styles.td}>
              Phone number (only when you sign up, log in, or add a phone number
              with an SMS-verified code)
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Anthropic</td>
            <td style={styles.td}>
              Reading receipt photos and suggesting expense categories (optional
              AI-assisted features, section 1.6)
            </td>
            <td style={styles.td}>
              Receipt images and/or typed expense descriptions, only when you
              choose to use these features
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Google Play Billing</td>
            <td style={styles.td}>
              Processing optional in-app credit purchases (section 1.8)
            </td>
            <td style={styles.td}>
              Purchase confirmation only — we never receive your payment card or
              bank details
            </td>
          </tr>
        </tbody>
      </table>
      <p style={styles.p}>
        We do not share your personal data with any other third party for their
        own marketing purposes. Contacts data used by the &quot;Find
        friends&quot; feature (section 1.2) is sent only to our own servers, not
        to any third party.
      </p>

      <h2 style={styles.h2}>5. Data Security</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          Passwords are stored using one-way bcrypt hashing — we cannot recover
          or view your original password.
        </li>
        <li style={styles.li}>
          Verification codes (for email or phone) are stored as a one-way hash,
          expire a short time after being sent, and can only be attempted a
          limited number of times before you must request a new one.
        </li>
        <li style={styles.li}>
          All communication between the app and our servers is encrypted in
          transit via HTTPS/TLS.
        </li>
        <li style={styles.li}>
          Access and refresh tokens are stored securely on your device using
          platform-provided secure storage (Android Keystore / iOS Keychain via
          Expo SecureStore).
        </li>
        <li style={styles.li}>
          We apply rate-limiting and authentication safeguards to protect
          against abuse and unauthorized access.
        </li>
      </ul>
      <p style={styles.p}>
        No method of transmission or storage is 100% secure, and we cannot
        guarantee absolute security, but we take reasonable, industry-standard
        measures to protect your information.
      </p>

      <h2 style={styles.h2}>6. Data Retention</h2>
      <p style={styles.p}>
        We retain your account and financial-record data for as long as your
        account is active. If you delete your account, we will delete or
        anonymize your personal information within 30 days, except where
        we&apos;re required to retain certain records by law, or where data is
        necessary to preserve the integrity of other users&apos; shared
        financial records (e.g. an expense you were involved in may remain
        visible to other group members in an anonymized form, since deleting it
        would corrupt their balance history). Contact information submitted
        through the &quot;Find friends&quot; feature is not retained at all —
        see section 1.2. Receipt photos are retained only for as long as needed
        to process them and for you to review the result, and are deleted
        automatically afterward unless you choose to keep an expense that
        references one.
      </p>

      <h2 style={styles.h2}>7. Your Rights and Choices</h2>
      <p style={styles.p}>
        Depending on your location, you may have the right to:
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>Access the personal data we hold about you</li>
        <li style={styles.li}>Correct inaccurate data</li>
        <li style={styles.li}>
          Request deletion of your account and associated data
        </li>
        <li style={styles.li}>
          Withdraw consent for optional data (e.g. phone number) at any time by
          removing it from your profile
        </li>
        <li style={styles.li}>
          Add and verify an additional email address or phone number on your
          account, or remove one — as long as at least one verified way to log
          in always remains on the account
        </li>
        <li style={styles.li}>
          Revoke contacts permission at any time via your device settings, which
          immediately stops the App from being able to read your contacts
        </li>
        <li style={styles.li}>
          Stop using the receipt scanning or category suggestion features at any
          time and enter expenses manually instead
        </li>
      </ul>
      <p style={styles.p}>
        To exercise any of these rights, contact us at{" "}
        <a href="mailto:help@splenza.in" style={styles.a}>
          help@splenza.in
        </a>
        .
      </p>

      <h2 style={styles.h2}>8. Children&apos;s Privacy</h2>
      <p style={styles.p}>
        Splenza is not directed at children under 13 (or the relevant minimum
        age in your jurisdiction), and we do not knowingly collect personal
        information from children. If you believe a child has provided us with
        personal information, contact us and we will delete it.
      </p>

      <h2 style={styles.h2}>9. Changes to This Policy</h2>
      <p style={styles.p}>
        We may update this Privacy Policy from time to time. We will notify you
        of material changes via the app or email, and update the &quot;Last
        updated&quot; date above.
      </p>

      <h2 style={styles.h2}>10. Contact Us</h2>
      <div style={styles.contactBox}>
        <p style={{ margin: 0 }}>
          <strong>help@splenza.in</strong>
        </p>
        <p style={{ margin: 0 }}>Splenza</p>
      </div>

      <hr style={styles.hr} />
      <p style={{ color: "#666666", fontSize: 13 }}>
        Splenza — Split expenses, not friendships.
      </p>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 760,
    margin: "0 auto",
    padding: "48px 24px 96px",
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
    lineHeight: 1.65,
    color: "#1A1A1A",
  },
  h1: { fontSize: 28, fontWeight: 800, marginBottom: 4 },
  updated: { color: "#666666", fontSize: 14, marginBottom: 32 },
  h2: {
    fontSize: 19,
    fontWeight: 700,
    marginTop: 40,
    marginBottom: 12,
    borderBottom: "1px solid #E5E5E5",
    paddingBottom: 8,
  },
  p: { margin: "12px 0", fontSize: 15 },
  a: { color: "#4B4FE0", textDecoration: "none" },
  table: {
    width: "100%",
    borderCollapse: "collapse",
    margin: "16px 0",
    fontSize: 14,
  },
  th: {
    textAlign: "left",
    padding: "10px 12px",
    border: "1px solid #E5E5E5",
    background: "#F0F0FA",
    fontWeight: 700,
  },
  td: {
    textAlign: "left",
    padding: "10px 12px",
    border: "1px solid #E5E5E5",
    verticalAlign: "top",
  },
  ul: { paddingLeft: 20 },
  li: { margin: "6px 0", fontSize: 15 },
  contactBox: {
    background: "#F0F0FA",
    border: "1px solid #E5E5E5",
    borderRadius: 12,
    padding: 20,
    marginTop: 16,
  },
  hr: { border: "none", borderTop: "1px solid #E5E5E5", margin: "32px 0" },
};
