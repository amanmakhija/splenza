import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  TextInput,
  ActivityIndicator,
  Share,
  Linking,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Users, RefreshCw, Search } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { normalizePhoneNumber } from "@/lib/phoneFormat";
import {
  DeviceContact,
  ContactsPermissionStatus,
  getContactsPermissionStatus,
  requestContactsPermission,
  getDeviceContacts,
} from "@/lib/contacts";
import { useFriendsQuery } from "@/hooks/useFriendsQuery";
import { UserLookupMatch } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { SegmentedControl } from "@/components/SegmentedControl";
import { ScreenHeader } from "@/components/ScreenHeader";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "AddFriend">;
type Mode = "email" | "phone";

/**
 * NOTE: this hits a contacts-lookup endpoint that doesn't exist elsewhere in
 * this codebase yet - it needs a backend route that accepts a batch of
 * phone numbers/emails and returns which ones belong to a registered user.
 * If it 404s or errors for any reason, we treat it as "no matches" so every
 * contact just falls back to showing an Invite button instead of breaking
 * the screen.
 */
async function lookupUsers(
  phoneNumbers: string[],
  emails: string[],
): Promise<UserLookupMatch[]> {
  try {
    const { data } = await apiClient.post<UserLookupMatch[]>(
      "/api/v1/users/lookup",
      { phoneNumbers, emails },
    );
    return data;
  } catch {
    return [];
  }
}

export function AddFriendScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();

  const [mode, setMode] = useState<Mode>("email");
  const [value, setValue] = useState("");
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [contactsPermission, setContactsPermission] =
    useState<ContactsPermissionStatus>("undetermined");
  const [contactsLoading, setContactsLoading] = useState(false);
  const [contacts, setContacts] = useState<DeviceContact[]>([]);
  const [contactsSearch, setContactsSearch] = useState("");
  const [lookupMatches, setLookupMatches] = useState<
    Map<string, UserLookupMatch>
  >(new Map());
  // No backend endpoint exists yet for "requests I've already sent that are
  // still pending" (FriendRequestDto only models incoming ones), so we track
  // who's been invited this session locally instead of hitting an endpoint
  // that doesn't exist. This resets if the screen remounts - a real
  // "sent requests" endpoint would make this durable across sessions.
  const [requestedContactIds, setRequestedContactIds] = useState<Set<string>>(
    new Set(),
  );

  const friendsQuery = useFriendsQuery();
  const friendUserIds = new Set((friendsQuery.data ?? []).map((f) => f.userId));

  const mutation = useMutation({
    mutationFn: () =>
      apiClient.post(
        "/api/v1/friends/requests",
        mode === "email"
          ? { email: value.trim() }
          : { phoneNumber: normalizePhoneNumber(value) },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friend-requests"] });
      setSuccessMessage("Friend request sent!");
      setValue("");
    },
  });

  const sendRequestMutation = useMutation({
    mutationFn: (params: { userId: string; contactId: string }) =>
      apiClient.post("/api/v1/friends/requests", { userId: params.userId }),
    onSuccess: (_data, params) => {
      queryClient.invalidateQueries({ queryKey: ["friend-requests"] });
      setRequestedContactIds((prev) => new Set(prev).add(params.contactId));
    },
  });

  const loadContacts = async () => {
    setContactsLoading(true);
    try {
      const list = await getDeviceContacts();
      setContacts(list);

      const allPhones = Array.from(
        new Set(list.flatMap((c) => c.phoneNumbers)),
      );
      const allEmails = Array.from(new Set(list.flatMap((c) => c.emails)));
      const matches = await lookupUsers(allPhones, allEmails);
      setLookupMatches(new Map(matches.map((m) => [m.matchedValue, m])));
    } finally {
      setContactsLoading(false);
    }
  };

  useEffect(() => {
    getContactsPermissionStatus().then((status) => {
      setContactsPermission(status);
      if (status === "granted") loadContacts();
    });
  }, []);

  const handleEnableContacts = async () => {
    const status = await requestContactsPermission();
    setContactsPermission(status);
    if (status === "granted") loadContacts();
  };

  const matchFor = (contact: DeviceContact): UserLookupMatch | undefined => {
    for (const phone of contact.phoneNumbers) {
      const m = lookupMatches.get(phone);
      if (m) return m;
    }
    for (const email of contact.emails) {
      const m = lookupMatches.get(email);
      if (m) return m;
    }
    return undefined;
  };

  const handleInvite = async (contact: DeviceContact) => {
    const firstName = contact.name.split(" ")[0];
    const message = `Hey ${firstName}! I'm using Splenza to split expenses with friends — download it here: https://splenza.in/download`;
    try {
      await Share.share({ message });
    } catch {
      // Share sheet dismissed/cancelled - nothing to do.
    }
  };

  const filteredContacts = contacts.filter((c) =>
    c.name.toLowerCase().includes(contactsSearch.toLowerCase()),
  );

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title="Add Friend" />
      <FlatList
        data={contactsPermission === "granted" ? filteredContacts : []}
        keyExtractor={(c) => c.id}
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        ListHeaderComponent={
          <>
            <Text style={[styles.description, { color: theme.textSecondary }]}>
              Find your friend by their email address or phone number. They'll
              need to accept your request before you can split expenses
              together.
            </Text>

            <SegmentedControl
              value={mode}
              onChange={(m) => {
                setMode(m);
                setValue("");
                setSuccessMessage(null);
              }}
              options={[
                { label: "Email", value: "email" },
                { label: "Phone", value: "phone" },
              ]}
            />

            <View style={{ marginTop: 20 }}>
              <TextField
                label={mode === "email" ? "Email address" : "Phone number"}
                value={value}
                onChangeText={(v) => {
                  setValue(mode === "phone" ? normalizePhoneNumber(v) : v);
                  setSuccessMessage(null);
                }}
                placeholder={
                  mode === "email" ? "friend@example.com" : "+919876543210"
                }
                keyboardType={mode === "email" ? "email-address" : "phone-pad"}
                autoCapitalize="none"
              />
            </View>

            {mutation.isError ? (
              <Text style={[styles.message, { color: theme.danger }]}>
                {getApiErrorMessage(mutation.error)}
              </Text>
            ) : null}
            {successMessage ? (
              <Text style={[styles.message, { color: theme.success }]}>
                {successMessage}
              </Text>
            ) : null}

            <Button
              title="Send Friend Request"
              onPress={() => mutation.mutate()}
              loading={mutation.isPending}
              disabled={!value.trim()}
              style={{ marginTop: 12 }}
            />

            <View style={[styles.divider, { backgroundColor: theme.border }]} />

            <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
              From your contacts
            </Text>

            {contactsPermission !== "granted" ? (
              <View
                style={[
                  styles.card,
                  {
                    backgroundColor: theme.surface,
                    borderColor: theme.border,
                  },
                ]}
              >
                <Users size={28} color={theme.primary} />
                <Text style={[styles.cardText, { color: theme.textSecondary }]}>
                  See which of your contacts are already on Splenza, and invite
                  the ones who aren't.
                </Text>
                {contactsPermission === "denied" ? (
                  <>
                    <Text
                      style={[styles.cardSubtext, { color: theme.textMuted }]}
                    >
                      Contacts access is turned off. Enable it in Settings to
                      use this.
                    </Text>
                    <Button
                      title="Open Settings"
                      variant="outline"
                      onPress={() => Linking.openSettings()}
                      style={{ marginTop: 12, alignSelf: "stretch" }}
                    />
                  </>
                ) : (
                  <Button
                    title="Allow contacts access"
                    onPress={handleEnableContacts}
                    style={{ marginTop: 12, alignSelf: "stretch" }}
                  />
                )}
              </View>
            ) : (
              <>
                <View style={styles.contactsHeaderRow}>
                  <View
                    style={[
                      styles.searchBox,
                      {
                        backgroundColor: theme.surface,
                        borderColor: theme.border,
                      },
                    ]}
                  >
                    <Search size={16} color={theme.textMuted} />
                    <TextInput
                      value={contactsSearch}
                      onChangeText={setContactsSearch}
                      placeholder="Search contacts"
                      placeholderTextColor={theme.textMuted}
                      autoCapitalize="none"
                      style={[styles.searchInput, { color: theme.textPrimary }]}
                    />
                  </View>
                  <Pressable
                    onPress={loadContacts}
                    disabled={contactsLoading}
                    accessibilityLabel="Refresh contacts"
                    accessibilityRole="button"
                    style={[
                      styles.refreshButton,
                      {
                        backgroundColor: theme.surface,
                        borderColor: theme.border,
                      },
                    ]}
                  >
                    <RefreshCw size={16} color={theme.textSecondary} />
                  </Pressable>
                </View>
                {contactsLoading ? (
                  <ActivityIndicator
                    color={theme.primary}
                    style={{ marginTop: 24 }}
                  />
                ) : null}
              </>
            )}
          </>
        }
        renderItem={({ item }) => {
          const match = matchFor(item);
          const isFriend = match && friendUserIds.has(match.userId);
          const isRequested = requestedContactIds.has(item.id);

          return (
            <View style={[styles.contactRow, { borderColor: theme.border }]}>
              <View style={styles.rowBodyFlex}>
                <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                  {item.name}
                </Text>
                <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                  {item.phoneNumbers[0] ?? item.emails[0]}
                </Text>
              </View>

              {isFriend ? (
                <Text style={[styles.statusLabel, { color: theme.textMuted }]}>
                  Friends
                </Text>
              ) : isRequested ? (
                <Text style={[styles.statusLabel, { color: theme.textMuted }]}>
                  Requested
                </Text>
              ) : match ? (
                <Pressable
                  onPress={() =>
                    sendRequestMutation.mutate({
                      userId: match.userId,
                      contactId: item.id,
                    })
                  }
                  disabled={sendRequestMutation.isPending}
                  style={[
                    styles.actionButton,
                    { backgroundColor: theme.primary },
                  ]}
                >
                  <Text style={styles.actionButtonText}>Add</Text>
                </Pressable>
              ) : (
                <Pressable
                  onPress={() => handleInvite(item)}
                  style={[
                    styles.actionButton,
                    styles.inviteButton,
                    { borderColor: theme.primary },
                  ]}
                >
                  <Text
                    style={[styles.actionButtonText, { color: theme.primary }]}
                  >
                    Invite
                  </Text>
                </Pressable>
              )}
            </View>
          );
        }}
        ListEmptyComponent={
          contactsPermission === "granted" && !contactsLoading ? (
            <Text style={[styles.emptyText, { color: theme.textMuted }]}>
              {contactsSearch
                ? "No contacts match your search."
                : "No contacts with a phone number or email were found."}
            </Text>
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  description: { fontSize: 14, lineHeight: 20, marginBottom: 24 },
  message: {
    textAlign: "center",
    marginTop: 16,
    fontSize: 13,
    fontWeight: "600",
  },

  divider: { height: 1, marginVertical: 28 },
  sectionTitle: { fontSize: 15, fontWeight: "800", marginBottom: 14 },

  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 20,
    alignItems: "center",
  },
  cardText: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: "center",
    marginTop: 12,
  },
  cardSubtext: {
    fontSize: 12,
    textAlign: "center",
    marginTop: 10,
  },

  contactsHeaderRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 4,
  },
  searchBox: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    height: 44,
  },
  searchInput: { flex: 1, fontSize: 14, height: "100%" },
  refreshButton: {
    width: 44,
    height: 44,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },

  contactRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
    borderBottomWidth: 1,
  },
  rowBodyFlex: { flex: 1, paddingRight: 12 },
  statusLabel: { fontSize: 12, fontWeight: "700" },
  actionButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 10,
  },
  inviteButton: { borderWidth: 1.5, backgroundColor: "transparent" },
  actionButtonText: { color: "#fff", fontWeight: "700", fontSize: 12 },

  emptyText: {
    textAlign: "center",
    marginTop: 24,
    fontSize: 13,
  },
});
