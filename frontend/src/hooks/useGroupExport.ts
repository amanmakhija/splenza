import { useState } from "react";
import { downloadAndShare } from "@/lib/exportFile";

export function useGroupExport(groupId: string, groupName: string | undefined) {
  const [exporting, setExporting] = useState<"csv" | "pdf" | null>(null);

  const handleExportCsv = async () => {
    setExporting("csv");
    await downloadAndShare(
      `/api/v1/export/csv/group/${groupId}`,
      `${groupName ?? "splenza"}.csv`,
      "text/csv",
    );
    setExporting(null);
  };

  const handleExportPdf = async () => {
    setExporting("pdf");
    await downloadAndShare(
      `/api/v1/export/pdf/group/${groupId}`,
      `${groupName ?? "splenza"}.pdf`,
      "application/pdf",
    );
    setExporting(null);
  };

  return { exporting, handleExportCsv, handleExportPdf };
}
