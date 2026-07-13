import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  ActivityIndicator,
  Modal,
  FlatList,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system";
import {
  FileUp,
  CheckCircle2,
  XCircle,
  ChevronRight,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import {
  CsvParseError,
  parseSplitwiseCsv,
  summarizeParsedCsv,
} from "@/lib/csvImport";
import {
  ExecuteImportPayload,
  Friend,
  Group,
  ImportResultResponse,
  ParsedCsv,
} from "@/types/api";
import { Button } from "@/components/Button";
import { TextField } from "@/components/TextField";
import { SegmentedControl } from "@/components/SegmentedControl";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "ImportCsv">;
type Step = "pick" | "group" | "mapping" | "preview" | "result";
type GroupMode = "new" | "existing";

async function fetchFriends(): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>("/api/v1/friends");
  return data;
}
async function fetchGroups(): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups");
  return data;
}
async function fetchGroup(groupId: string): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/api/v1/groups/${groupId}`);
  return data;
}

export function ImportCsvScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const [step, setStep] = useState<Step>("pick");
  const [fileName, setFileName] = useState<string | undefined>();
  const [parseError, setParseError] = useState<string | null>(null);
  const [parsed, setParsed] = useState<ParsedCsv | null>(null);

  const [groupMode, setGroupMode] = useState<GroupMode>("new");
  const [newGroupName, setNewGroupName] = useState("");
  const [existingGroupId, setExistingGroupId] = useState<string | null>(null);

  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [pickerOpenFor, setPickerOpenFor] = useState<string | null>(null);

  const [result, setResult] = useState<ImportResultResponse | null>(null);

  const friendsQuery = useQuery({
    queryKey: ["friends"],
    queryFn: fetchFriends,
  });
  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: fetchGroups,
    enabled: groupMode === "existing",
  });
  const existingGroupDetailQuery = useQuery({
    queryKey: ["group", existingGroupId],
    queryFn: () => fetchGroup(existingGroupId as string),
    enabled: Boolean(existingGroupId),
  });

  const summary = useMemo(
    () => (parsed ? summarizeParsedCsv(parsed) : null),
    [parsed],
  );

  // Who can be mapped to: for a new group, any friend + yourself. For an existing group, only
  // people already in that group (import can't invite new members into an existing group).
  const mappingCandidates: { id: string; name: string }[] = useMemo(() => {
    if (!currentUser) return [];
    if (groupMode === "existing" && existingGroupDetailQuery.data) {
      return existingGroupDetailQuery.data.members.map((m) => ({
        id: m.userId,
        name: m.name,
      }));
    }
    const friends = (friendsQuery.data ?? []).map((f) => ({
      id: f.userId,
      name: f.name,
    }));
    return [
      { id: currentUser.id, name: `${currentUser.name} (you)` },
      ...friends,
    ];
  }, [
    groupMode,
    existingGroupDetailQuery.data,
    friendsQuery.data,
    currentUser,
  ]);

  const importMutation = useMutation({
    mutationFn: (payload: ExecuteImportPayload) =>
      apiClient.post<ImportResultResponse>("/api/v1/import/execute", payload),
    onSuccess: (res) => {
      setResult(res.data);
      setStep("result");
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });

  const pickFile = async () => {
    setParseError(null);
    const picked = await DocumentPicker.getDocumentAsync({
      type: [
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "text/plain",
        "*/*",
      ],
      copyToCacheDirectory: true,
    });
    if (picked.canceled || !picked.assets?.[0]) return;

    const asset = picked.assets[0];
    try {
      const content = await FileSystem.readAsStringAsync(asset.uri);
      const parsedCsv = parseSplitwiseCsv(content);
      setFileName(asset.name);
      setParsed(parsedCsv);
    } catch (err) {
      setParseError(
        err instanceof CsvParseError
          ? err.message
          : "Could not read that file. Please try again.",
      );
      setParsed(null);
    }
  };

  const canContinueFromGroup =
    groupMode === "new"
      ? newGroupName.trim().length > 0
      : Boolean(existingGroupId);

  const selfMappedCount = Object.values(mapping).filter(
    (id) => id === currentUser?.id,
  ).length;
  const allMembersMapped = parsed
    ? parsed.members.every((m) => mapping[m])
    : false;
  const mappedValues = Object.values(mapping);
  const hasDuplicateMapping =
    new Set(mappedValues).size !== mappedValues.length;
  const canContinueFromMapping =
    allMembersMapped && selfMappedCount === 1 && !hasDuplicateMapping;

  /** Candidates for a given CSV member, excluding anyone already mapped to a DIFFERENT member -
   *  each Splenza account can only be used once across the whole mapping. */
  const candidatesFor = (memberName: string | null) => {
    if (!memberName) return [];
    const usedByOthers = new Set(
      Object.entries(mapping)
        .filter(([name]) => name !== memberName)
        .map(([, id]) => id),
    );
    return mappingCandidates.filter((c) => !usedByOthers.has(c.id));
  };

  const setMemberMapping = (memberName: string, userId: string) => {
    setMapping((prev) => ({ ...prev, [memberName]: userId }));
    setPickerOpenFor(null);
  };

  const handleConfirmImport = () => {
    if (!parsed) return;
    const payload: ExecuteImportPayload = {
      groupId: groupMode === "existing" ? existingGroupId : null,
      newGroupName: groupMode === "new" ? newGroupName.trim() : null,
      memberMapping: mapping,
      fileName,
      rows: parsed.rows,
    };
    importMutation.mutate(payload);
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        {step === "pick" && (
          <View>
            <Text style={[styles.stepTitle, { color: theme.textPrimary }]}>
              Import from Splitwise
            </Text>
            <Text
              style={[styles.stepDescription, { color: theme.textSecondary }]}
            >
              Export your expenses from Splitwise as a CSV (Splitwise → Group
              settings → Export as CSV), then pick that file here. We'll rebuild
              every expense, payer, and split exactly as they were recorded.
            </Text>

            <Pressable
              onPress={pickFile}
              style={[styles.pickButton, { borderColor: theme.primary }]}
            >
              <FileUp size={22} color={theme.primary} />
              <Text style={{ color: theme.primary, fontWeight: "700" }}>
                {fileName ? "Choose a different file" : "Choose CSV file"}
              </Text>
            </Pressable>

            {fileName ? (
              <Text
                style={{ color: theme.textMuted, marginTop: 8, fontSize: 12 }}
              >
                {fileName}
              </Text>
            ) : null}
            {parseError ? (
              <Text style={[styles.errorText, { color: theme.danger }]}>
                {parseError}
              </Text>
            ) : null}

            {summary && parsed ? (
              <View
                style={[
                  styles.summaryCard,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <SummaryRow
                  label="Expenses found"
                  value={String(summary.expenseCount)}
                  theme={theme}
                />
                <SummaryRow
                  label="Payments found"
                  value={String(summary.paymentCount)}
                  theme={theme}
                />
                <SummaryRow
                  label="Total expense amount"
                  value={`₹${summary.totalAmount.toFixed(2)}`}
                  theme={theme}
                />
                <SummaryRow
                  label="Date range"
                  value={`${summary.dateFrom} → ${summary.dateTo}`}
                  theme={theme}
                />
                <SummaryRow
                  label="Members found"
                  value={parsed.members.join(", ")}
                  theme={theme}
                />
              </View>
            ) : null}

            <Button
              title="Continue"
              onPress={() => setStep("group")}
              disabled={!parsed}
              style={{ marginTop: 20 }}
            />
          </View>
        )}

        {step === "group" && (
          <View>
            <Text style={[styles.stepTitle, { color: theme.textPrimary }]}>
              Where should this go?
            </Text>
            <SegmentedControl
              value={groupMode}
              onChange={(m) => {
                setGroupMode(m);
                setExistingGroupId(null);
                setMapping({});
              }}
              options={[
                { label: "Create new group", value: "new" },
                { label: "Use existing group", value: "existing" },
              ]}
            />

            {groupMode === "new" ? (
              <View style={{ marginTop: 20 }}>
                <TextField
                  label="New group name"
                  value={newGroupName}
                  onChangeText={setNewGroupName}
                  placeholder="e.g. Shimla Trip"
                />
              </View>
            ) : (
              <View style={{ marginTop: 20 }}>
                {groupsQuery.isLoading ? (
                  <ActivityIndicator color={theme.primary} />
                ) : (
                  (groupsQuery.data ?? []).map((g) => (
                    <Pressable
                      key={g.id}
                      onPress={() => {
                        setExistingGroupId(g.id);
                        setMapping({});
                      }}
                      style={[
                        styles.groupOption,
                        {
                          backgroundColor:
                            existingGroupId === g.id
                              ? theme.primary
                              : theme.surface,
                          borderColor:
                            existingGroupId === g.id
                              ? theme.primary
                              : theme.border,
                        },
                      ]}
                    >
                      <Text
                        style={{
                          color:
                            existingGroupId === g.id
                              ? "#fff"
                              : theme.textPrimary,
                          fontWeight: "600",
                        }}
                      >
                        {g.name}
                      </Text>
                      <Text
                        style={{
                          color:
                            existingGroupId === g.id
                              ? "rgba(255,255,255,0.8)"
                              : theme.textMuted,
                          fontSize: 12,
                        }}
                      >
                        {g.members.length} members
                      </Text>
                    </Pressable>
                  ))
                )}
                {groupMode === "existing" ? (
                  <Text
                    style={{
                      color: theme.textMuted,
                      fontSize: 12,
                      marginTop: 8,
                    }}
                  >
                    You can only map CSV members to people already in this
                    group.
                  </Text>
                ) : null}
              </View>
            )}

            <Button
              title="Continue"
              onPress={() => setStep("mapping")}
              disabled={!canContinueFromGroup}
              style={{ marginTop: 24 }}
            />
          </View>
        )}

        {step === "mapping" && parsed && (
          <View>
            <Text style={[styles.stepTitle, { color: theme.textPrimary }]}>
              Map CSV members
            </Text>
            <Text
              style={[styles.stepDescription, { color: theme.textSecondary }]}
            >
              Match each name from the CSV to a Splenza account. Exactly one
              must be mapped to you.
            </Text>

            {parsed.members.map((memberName) => {
              const mappedId = mapping[memberName];
              const mappedCandidate = mappingCandidates.find(
                (c) => c.id === mappedId,
              );
              return (
                <Pressable
                  key={memberName}
                  onPress={() => setPickerOpenFor(memberName)}
                  style={[
                    styles.mappingRow,
                    {
                      backgroundColor: theme.surface,
                      borderColor: theme.border,
                    },
                  ]}
                >
                  <View style={styles.rowBody}>
                    <Text
                      style={{ color: theme.textPrimary, fontWeight: "700" }}
                    >
                      {memberName}
                    </Text>
                    <Text
                      style={{
                        color: mappedCandidate
                          ? theme.primary
                          : theme.textMuted,
                        fontSize: 13,
                        marginTop: 2,
                      }}
                    >
                      {mappedCandidate ? mappedCandidate.name : "Tap to map..."}
                    </Text>
                  </View>
                  <ChevronRight size={18} color={theme.textMuted} />
                </Pressable>
              );
            })}

            {!allMembersMapped ? (
              <Text style={[styles.errorText, { color: theme.danger }]}>
                Map every member before continuing.
              </Text>
            ) : selfMappedCount !== 1 ? (
              <Text style={[styles.errorText, { color: theme.danger }]}>
                Exactly one member must be mapped to you.
              </Text>
            ) : hasDuplicateMapping ? (
              <Text style={[styles.errorText, { color: theme.danger }]}>
                The same person is mapped to more than one CSV member - each
                person can only be used once.
              </Text>
            ) : null}

            <Button
              title="Preview import"
              onPress={() => setStep("preview")}
              disabled={!canContinueFromMapping}
              style={{ marginTop: 20 }}
            />

            <Modal
              visible={Boolean(pickerOpenFor)}
              animationType="slide"
              transparent
              onRequestClose={() => setPickerOpenFor(null)}
            >
              <View style={styles.modalOverlay}>
                <View
                  style={[
                    styles.modalSheet,
                    { backgroundColor: theme.surface },
                  ]}
                >
                  <Text
                    style={[styles.modalTitle, { color: theme.textPrimary }]}
                  >
                    Map "{pickerOpenFor}" to...
                  </Text>
                  <FlatList
                    data={candidatesFor(pickerOpenFor)}
                    keyExtractor={(c) => c.id}
                    renderItem={({ item }) => (
                      <Pressable
                        onPress={() =>
                          pickerOpenFor &&
                          setMemberMapping(pickerOpenFor, item.id)
                        }
                        style={styles.candidateRow}
                      >
                        <Text style={{ color: theme.textPrimary }}>
                          {item.name}
                        </Text>
                      </Pressable>
                    )}
                    ListEmptyComponent={
                      <Text style={{ color: theme.textMuted, padding: 12 }}>
                        No candidates left - everyone available is already
                        mapped to a different CSV member.
                      </Text>
                    }
                  />
                </View>
              </View>
            </Modal>
          </View>
        )}

        {step === "preview" && parsed && (
          <View>
            <Text style={[styles.stepTitle, { color: theme.textPrimary }]}>
              Review before importing
            </Text>
            <View
              style={[
                styles.summaryCard,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <SummaryRow
                label="Group"
                value={
                  groupMode === "new"
                    ? newGroupName
                    : (existingGroupDetailQuery.data?.name ?? "")
                }
                theme={theme}
              />
              <SummaryRow
                label="Expenses to create"
                value={String(summary?.expenseCount ?? 0)}
                theme={theme}
              />
              <SummaryRow
                label="Settlements to record"
                value={String(summary?.paymentCount ?? 0)}
                theme={theme}
              />
            </View>

            <Text
              style={{
                color: theme.textMuted,
                fontSize: 12,
                marginTop: 16,
                marginBottom: 8,
              }}
            >
              First {Math.min(5, parsed.rows.length)} of {parsed.rows.length}{" "}
              rows:
            </Text>
            {parsed.rows.slice(0, 5).map((row, idx) => (
              <View
                key={idx}
                style={[
                  styles.previewRow,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                  {row.description}
                </Text>
                <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                  {row.date} · ₹{row.cost.toFixed(2)} · {row.category}
                </Text>
              </View>
            ))}

            {importMutation.isError ? (
              <Text style={[styles.errorText, { color: theme.danger }]}>
                {getApiErrorMessage(importMutation.error)}
              </Text>
            ) : null}

            <Button
              title={`Import ${parsed.rows.length} rows`}
              onPress={handleConfirmImport}
              loading={importMutation.isPending}
              style={{ marginTop: 20 }}
            />
          </View>
        )}

        {step === "result" && result && (
          <View style={styles.resultWrap}>
            {result.failedRows === 0 ? (
              <CheckCircle2 size={56} color={theme.success} />
            ) : result.importedRows === 0 ? (
              <XCircle size={56} color={theme.danger} />
            ) : (
              <CheckCircle2 size={56} color={theme.warning} />
            )}

            <Text
              style={[
                styles.stepTitle,
                {
                  color: theme.textPrimary,
                  marginTop: 16,
                  textAlign: "center",
                },
              ]}
            >
              Imported {result.importedRows} of {result.totalRows} rows
            </Text>

            {result.failedRows > 0 ? (
              <View
                style={[
                  styles.summaryCard,
                  {
                    backgroundColor: theme.surface,
                    borderColor: theme.border,
                    marginTop: 16,
                  },
                ]}
              >
                <Text
                  style={{
                    color: theme.textPrimary,
                    fontWeight: "700",
                    marginBottom: 8,
                  }}
                >
                  {result.failedRows} row{result.failedRows === 1 ? "" : "s"}{" "}
                  couldn't be imported:
                </Text>
                {result.errors.map((e, idx) => (
                  <Text
                    key={idx}
                    style={{
                      color: theme.textMuted,
                      fontSize: 12,
                      marginBottom: 4,
                    }}
                  >
                    Row {e.rowIndex + 1} ({e.description}): {e.reason}
                  </Text>
                ))}
              </View>
            ) : null}

            <Button
              title="View Group"
              onPress={() =>
                navigation.navigate("Tabs", {
                  screen: "Groups",
                  params: {
                    screen: "GroupDetail",
                    params: {
                      groupId: result.groupId,
                      groupName: newGroupName || "Group",
                    },
                  },
                })
              }
              style={{ marginTop: 24, width: "100%" }}
            />
            <Button
              title="Done"
              variant="outline"
              onPress={() => navigation.goBack()}
              style={{ marginTop: 12, width: "100%" }}
            />
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function SummaryRow({
  label,
  value,
  theme,
}: {
  label: string;
  value: string;
  theme: ReturnType<typeof useAppTheme>["theme"];
}) {
  return (
    <View style={styles.summaryRow}>
      <Text style={{ color: theme.textMuted, fontSize: 13 }}>{label}</Text>
      <Text
        style={{
          color: theme.textPrimary,
          fontSize: 13,
          fontWeight: "600",
          flexShrink: 1,
          textAlign: "right",
        }}
      >
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 60 },
  stepTitle: { fontSize: 20, fontWeight: "800", marginBottom: 8 },
  stepDescription: { fontSize: 14, lineHeight: 20, marginBottom: 20 },
  pickButton: {
    flexDirection: "row",
    gap: 10,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderStyle: "dashed",
    borderRadius: 14,
    paddingVertical: 20,
  },
  errorText: { fontSize: 13, marginTop: 12, textAlign: "center" },
  summaryCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    marginTop: 20,
    gap: 10,
  },
  summaryRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 12,
  },
  groupOption: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 14,
    marginBottom: 10,
  },
  mappingRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  rowBody: { flex: 1 },
  previewRow: {
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 8,
  },
  resultWrap: { alignItems: "center", paddingTop: 40 },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "flex-end",
  },
  modalSheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 20,
    maxHeight: "60%",
  },
  modalTitle: { fontSize: 16, fontWeight: "800", marginBottom: 12 },
  candidateRow: {
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#00000022",
  },
});
