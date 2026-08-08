/**
 * Compares two "1.2.3"-style version strings.
 * Returns negative if a < b, 0 if equal, positive if a > b.
 * Missing/non-numeric segments are treated as 0, so "1.2" == "1.2.0".
 */
export function compareVersions(a: string, b: string): number {
  const partsA = a.split(".").map((n) => parseInt(n, 10) || 0);
  const partsB = b.split(".").map((n) => parseInt(n, 10) || 0);
  const length = Math.max(partsA.length, partsB.length);

  for (let i = 0; i < length; i++) {
    const diff = (partsA[i] ?? 0) - (partsB[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

export function isVersionNewer(latest: string, current: string): boolean {
  return compareVersions(latest, current) > 0;
}
