/**
 * Decode a dynamic `[id]` route segment to the raw id (same reason as the ecommerce seller
 * detail page, TASK-PC-FE-133): Next delivers the segment percent-encoded, and the fan client
 * re-encodes the id for the upstream path — passing the still-encoded value would
 * double-encode it. A malformed encoding falls back to the raw segment.
 */
export function decodeSegment(segment: string): string {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}
