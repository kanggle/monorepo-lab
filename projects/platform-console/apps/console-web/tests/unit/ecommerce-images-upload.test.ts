import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Presigned byte-upload helper + image-types schemas (TASK-PC-FE-082).
 *
 * `uploadToPresignedUrl` is the DIRECT browser→S3 PUT (the one leg that does
 * NOT go through the api-client / proxy). The security-critical assertions:
 *   - it PUTs to the presigned `uploadUrl` with the file's content type;
 *   - it attaches NO `Authorization` header and `withCredentials=false` (the
 *     presign IS the authorization — a bearer/cookie would break the S3
 *     signature, and leaking the operator credential cross-origin to S3 would
 *     be a serious defect);
 *   - it reports progress and resolves on a 2xx, rejects (PresignedUploadError)
 *     on a non-2xx / network error so the caller never proceeds to register an
 *     un-uploaded object.
 */

import {
  uploadToPresignedUrl,
  PresignedUploadError,
} from '@/features/ecommerce-ops/hooks/use-ecommerce-images';
import {
  UpdateImageBodySchema,
  RegisterImageBodySchema,
  PresignedUrlBodySchema,
  ImageItemSchema,
  ImageListSchema,
  isAllowedImageContentType,
  IMAGE_ALLOWED_CONTENT_TYPES,
  IMAGE_MAX_BYTES,
} from '@/features/ecommerce-ops/api/image-types';

interface ProgressLike {
  lengthComputable: boolean;
  loaded: number;
  total: number;
}

class FakeXHR {
  static instances: FakeXHR[] = [];
  method = '';
  url = '';
  headers: Record<string, string> = {};
  withCredentials = false;
  status = 0;
  body: unknown = undefined;
  upload: { onprogress?: (e: ProgressLike) => void } = {};
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }
  setRequestHeader(k: string, v: string) {
    this.headers[k] = v;
  }
  send(body: unknown) {
    this.body = body;
    FakeXHR.instances.push(this);
  }
  abort() {
    this.onabort?.();
  }
}

function installFakeXHR() {
  FakeXHR.instances = [];
  vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest);
}

const URL = 'http://minio.local/product-images/products/p-1/0-abc.png?sig=x';

function pngFile(size = 1024): File {
  return new File([new Uint8Array(size)], 'photo.png', { type: 'image/png' });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('uploadToPresignedUrl — direct browser → S3 PUT', () => {
  it('PUTs to the presigned URL with the file content type, NO Authorization, no credentials', async () => {
    installFakeXHR();
    const file = pngFile();
    const p = uploadToPresignedUrl(URL, file);
    const xhr = FakeXHR.instances.at(-1)!;

    expect(xhr.method).toBe('PUT');
    expect(xhr.url).toBe(URL);
    expect(xhr.headers['Content-Type']).toBe('image/png');
    expect(xhr.headers['Authorization']).toBeUndefined();
    expect(xhr.withCredentials).toBe(false);
    expect(xhr.body).toBe(file);

    xhr.status = 200;
    xhr.onload!();
    await expect(p).resolves.toBeUndefined();
  });

  it('reports progress (0–1) and a final 1 on success', async () => {
    installFakeXHR();
    const onProgress = vi.fn();
    const p = uploadToPresignedUrl(URL, pngFile(), onProgress);
    const xhr = FakeXHR.instances.at(-1)!;

    xhr.upload.onprogress!({ lengthComputable: true, loaded: 25, total: 100 });
    xhr.upload.onprogress!({ lengthComputable: true, loaded: 80, total: 100 });
    xhr.status = 204;
    xhr.onload!();
    await p;

    expect(onProgress).toHaveBeenCalledWith(0.25);
    expect(onProgress).toHaveBeenCalledWith(0.8);
    expect(onProgress).toHaveBeenLastCalledWith(1);
  });

  it('rejects with PresignedUploadError on a non-2xx S3 response (caller must NOT register)', async () => {
    installFakeXHR();
    const p = uploadToPresignedUrl(URL, pngFile());
    const xhr = FakeXHR.instances.at(-1)!;
    xhr.status = 403;
    xhr.onload!();
    const err = await p.catch((e) => e);
    expect(err).toBeInstanceOf(PresignedUploadError);
    expect(err.status).toBe(403);
  });

  it('rejects with PresignedUploadError(0) on a network error', async () => {
    installFakeXHR();
    const p = uploadToPresignedUrl(URL, pngFile());
    const xhr = FakeXHR.instances.at(-1)!;
    xhr.onerror!();
    const err = await p.catch((e) => e);
    expect(err).toBeInstanceOf(PresignedUploadError);
    expect(err.status).toBe(0);
  });
});

describe('image-types — producer-matched shapes + tolerance', () => {
  it('isAllowedImageContentType matches the producer allow-list (jpeg/png/webp)', () => {
    for (const t of IMAGE_ALLOWED_CONTENT_TYPES) {
      expect(isAllowedImageContentType(t)).toBe(true);
    }
    expect(isAllowedImageContentType('image/gif')).toBe(false);
    expect(isAllowedImageContentType('application/pdf')).toBe(false);
  });

  it('IMAGE_MAX_BYTES mirrors the producer 5 MiB cap', () => {
    expect(IMAGE_MAX_BYTES).toBe(5 * 1024 * 1024);
  });

  it('PresignedUrlBodySchema requires a positive contentLength + non-empty contentType', () => {
    expect(PresignedUrlBodySchema.safeParse({ contentType: 'image/png', contentLength: 1 }).success).toBe(true);
    expect(PresignedUrlBodySchema.safeParse({ contentType: '', contentLength: 1 }).success).toBe(false);
    expect(PresignedUrlBodySchema.safeParse({ contentType: 'image/png', contentLength: 0 }).success).toBe(false);
  });

  it('RegisterImageBodySchema requires objectKey + non-negative sortOrder + isPrimary', () => {
    expect(
      RegisterImageBodySchema.safeParse({ objectKey: 'products/p/x.png', sortOrder: 0, isPrimary: false }).success,
    ).toBe(true);
    expect(RegisterImageBodySchema.safeParse({ objectKey: '', sortOrder: 0, isPrimary: false }).success).toBe(false);
    expect(
      RegisterImageBodySchema.safeParse({ objectKey: 'k', sortOrder: -1, isPrimary: false }).success,
    ).toBe(false);
  });

  it('UpdateImageBodySchema requires at least one of sortOrder / isPrimary (empty PATCH rejected)', () => {
    expect(UpdateImageBodySchema.safeParse({ isPrimary: true }).success).toBe(true);
    expect(UpdateImageBodySchema.safeParse({ sortOrder: 2 }).success).toBe(true);
    expect(UpdateImageBodySchema.safeParse({ sortOrder: 1, isPrimary: false }).success).toBe(true);
    expect(UpdateImageBodySchema.safeParse({}).success).toBe(false);
  });

  it('ImageItemSchema / ImageListSchema tolerate unknown fields and a missing uploadedAt', () => {
    const row = ImageItemSchema.parse({
      imageId: 'i', objectKey: 'k', sortOrder: 0, isPrimary: true, url: 'u', future: 'x',
    });
    expect(row.imageId).toBe('i');
    const list = ImageListSchema.parse({ images: [{ imageId: 'i', objectKey: 'k', sortOrder: 0, isPrimary: false, url: 'u' }] });
    expect(list.images).toHaveLength(1);
    expect(ImageListSchema.parse({}).images).toEqual([]);
  });
});
