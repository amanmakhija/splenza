import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Delete Your Account & Data — Splenza",
  description: "How to delete your Splenza account and associated data.",
};

export default function DeleteAccountPage() {
  return (
    <div style={styles.container}>
      <h1 style={styles.h1}>Delete Your Account & Data</h1>
      <p style={styles.updated}>
        Last updated: <strong>29/07/2026</strong>
      </p>

      <p style={styles.p}>
        You can permanently delete your Splenza account and associated data at
        any time. This page explains how, and exactly what happens when you do.
      </p>

      <h2 style={styles.h2}>Option 1: Delete In-App (Recommended)</h2>
      <ol style={styles.ol}>
        <li style={styles.li}>Open the Splenza app and log in</li>
        <li style={styles.li}>
          Go to <strong>Profile</strong>
        </li>
        <li style={styles.li}>
          Tap <strong>Delete account</strong>
        </li>
        <li style={styles.li}>
          Confirm the deletion when prompted. Your account will be deleted
          immediately.
        </li>
      </ol>

      <h2 style={styles.h2}>Option 2: Request Deletion by Email</h2>
      <p style={styles.p}>
        If you no longer have access to the app or your account, email us at{" "}
        <a href="mailto:help@splenza.in" style={styles.a}>
          help@splenza.in
        </a>{" "}
        from the email address associated with your account, with the subject
        line &quot;Account Deletion Request.&quot; We&apos;ll verify your
        identity and process the deletion within{" "}
        <strong>7 business days</strong>.
      </p>

      <h2 style={styles.h2}>What Gets Deleted</h2>
      <p style={styles.p}>
        When you delete your account, the following data is permanently removed:
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>Your name, email address, and phone number</li>
        <li style={styles.li}>Your profile picture</li>
        <li style={styles.li}>Your login credentials</li>
        <li style={styles.li}>Your device push notification token</li>
        <li style={styles.li}>Your friend connections</li>
      </ul>

      <h2 style={styles.h2}>What May Be Retained</h2>
      <p style={styles.p}>
        Some data may be retained or anonymized rather than fully deleted, for
        the following reasons:
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          <strong>Shared expense and settlement records:</strong> If you were
          part of a group expense or settlement with other users, the record
          itself may remain visible to the other members involved (e.g. so their
          balance history stays accurate), but it will be anonymized — your name
          will be replaced with a generic label like &quot;Deleted user&quot;
          and no longer linked to your account or contact information.
        </li>
        <li style={styles.li}>
          <strong>Legal or compliance records:</strong> Where we&apos;re
          required to retain certain data by applicable law.
        </li>
      </ul>
      <p style={styles.p}>
        No data is retained for marketing, analytics profiling, or any purpose
        beyond what&apos;s described above.
      </p>

      <h2 style={styles.h2}>How Long It Takes</h2>
      <p style={styles.p}>
        In-app deletion is immediate. Email requests are processed within{" "}
        <strong>7 business days</strong>. Backups (if any) that contain your
        data are purged within <strong>30 days</strong> of your deletion
        request.
      </p>

      <h2 style={styles.h2}>Questions?</h2>
      <div style={styles.contactBox}>
        <p style={{ margin: 0 }}>
          <strong>help@splenza.in</strong>
        </p>
        <p style={{ margin: 0 }}>Splenza</p>
      </div>

      <hr style={styles.hr} />
      <p style={{ color: "#666666", fontSize: 13 }}>
        See also our{" "}
        <a href="/privacy-policy" style={styles.a}>
          Privacy Policy
        </a>{" "}
        for more on how we handle your data.
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
  a: { color: "#4B4FE0", textDecoration: "none", fontWeight: 600 },
  ul: { paddingLeft: 20 },
  ol: { paddingLeft: 20 },
  li: { margin: "8px 0", fontSize: 15 },
  contactBox: {
    background: "#F0F0FA",
    border: "1px solid #E5E5E5",
    borderRadius: 12,
    padding: 20,
    marginTop: 16,
  },
  hr: { border: "none", borderTop: "1px solid #E5E5E5", margin: "32px 0" },
};
