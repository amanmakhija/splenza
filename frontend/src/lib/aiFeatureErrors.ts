/** Thrown specifically when the user is out of free + purchased credits for
 * an AI feature, so call sites can distinguish "buy more credits" from any
 * other failure. Shared across all AI features (receipt scan, voice entry,
 * etc.) so a single `instanceof` check works regardless of which feature
 * triggered it. */
export class InsufficientCreditsError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InsufficientCreditsError";
  }
}
