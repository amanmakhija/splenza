import * as Contacts from "expo-contacts";
import { normalizePhoneNumber } from "@/lib/phoneFormat";

export interface DeviceContact {
  id: string;
  name: string;
  /** Already normalized via normalizePhoneNumber - safe to match/send as-is. */
  phoneNumbers: string[];
  emails: string[];
}

export type ContactsPermissionStatus = "granted" | "denied" | "undetermined";

export async function getContactsPermissionStatus(): Promise<ContactsPermissionStatus> {
  const { status } = await Contacts.getPermissionsAsync();
  return status as ContactsPermissionStatus;
}

export async function requestContactsPermission(): Promise<ContactsPermissionStatus> {
  const { status } = await Contacts.requestPermissionsAsync();
  return status as ContactsPermissionStatus;
}

export async function getDeviceContacts(): Promise<DeviceContact[]> {
  const { data } = await Contacts.getContactsAsync({
    fields: [Contacts.Fields.PhoneNumbers, Contacts.Fields.Emails],
  });

  const contacts: DeviceContact[] = [];
  for (const c of data) {
    const phoneNumbers = (c.phoneNumbers ?? [])
      .map((p) => (p.number ? normalizePhoneNumber(p.number) : null))
      .filter((n): n is string => Boolean(n) && n!.length >= 6);
    const emails = (c.emails ?? [])
      .map((e) => e.email?.trim().toLowerCase())
      .filter((e): e is string => Boolean(e));

    // Skip contacts we have no way to match or invite by.
    if (phoneNumbers.length === 0 && emails.length === 0) continue;

    contacts.push({
      id: c.id ?? `${c.name}-${phoneNumbers[0] ?? emails[0]}`,
      name: c.name?.trim() || "Unknown",
      phoneNumbers,
      emails,
    });
  }

  return contacts.sort((a, b) => a.name.localeCompare(b.name));
}
