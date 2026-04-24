/**
 * Product image placeholder utilities.
 *
 * These helpers generate placeholder image URLs for products whose
 * backend response is missing `thumbnailUrl` / `images`. They are unrelated
 * to mock product data (the previous `MOCK_PRODUCTS` silent fallback has
 * been removed — see TASK-FE-061).
 */

function img(label: string, variant: number, size = 600): string {
  const colors = [
    'e2e8f0/475569',
    'dbeafe/1e40af',
    'fce7f3/be185d',
    'dcfce7/166534',
    'fef3c7/92400e',
    'ede9fe/5b21b6',
  ];
  const bg = colors[(variant - 1) % colors.length];
  return `https://placehold.co/${size}x${size}/${bg}?text=${encodeURIComponent(label)}+${variant}&font=noto-sans`;
}

export function fallbackThumbnail(name: string): string {
  return img(name, 1, 400);
}

export function fallbackImages(name: string): string[] {
  return [img(name, 1), img(name, 2), img(name, 3)];
}
