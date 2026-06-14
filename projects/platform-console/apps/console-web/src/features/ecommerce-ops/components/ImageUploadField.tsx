'use client';

import { useId, useRef, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useCreateUploadUrl,
  useRegisterImage,
  uploadToPresignedUrl,
  PresignedUploadError,
} from '../hooks/use-ecommerce-images';
import {
  IMAGE_ALLOWED_CONTENT_TYPES,
  IMAGE_MAX_BYTES,
  isAllowedImageContentType,
} from '../api/image-types';

/**
 * Presigned image upload control (TASK-PC-FE-082 — § 2.4.10 #11 + #12). The
 * three-step flow:
 *
 *   1. POST `…/images/upload-url` (proxy) → `{ uploadUrl, objectKey }`.
 *   2. **PUT the file bytes DIRECTLY to `uploadUrl`** (browser → S3, XHR
 *      progress; no credential — the presign is the authorization).
 *   3. POST `…/images` (proxy) → register `{ objectKey, sortOrder, isPrimary }`.
 *
 * Client-side content-type / size validation is a UX aid only (the producer
 * re-validates → 400 `MEDIA_VALIDATION_FAILED`); the trust boundary is the
 * producer. If the PUT (step 2) fails, step 3 is NOT attempted (no orphan
 * registration) and the operator is told to retry.
 */
export interface ImageUploadFieldProps {
  productId: string;
  /** sortOrder assigned to the new image (typically the current count). */
  nextSortOrder: number;
  /** Disabled when the product already holds the max images. */
  disabled?: boolean;
  /** Called after a successful register (#12) so the manager can refetch. */
  onUploaded?: () => void;
}

type Phase = 'idle' | 'requesting' | 'uploading' | 'registering';

const ACCEPT = IMAGE_ALLOWED_CONTENT_TYPES.join(',');

export function ImageUploadField({
  productId,
  nextSortOrder,
  disabled = false,
  onUploaded,
}: ImageUploadFieldProps) {
  const fileId = useId();
  const primaryId = useId();
  const inputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [isPrimary, setIsPrimary] = useState(false);
  const [phase, setPhase] = useState<Phase>('idle');
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const createUrl = useCreateUploadUrl();
  const register = useRegisterImage();
  const busy = phase !== 'idle';

  function validate(f: File): string | null {
    if (!isAllowedImageContentType(f.type)) {
      return `지원하지 않는 형식입니다. 허용: ${IMAGE_ALLOWED_CONTENT_TYPES.join(', ')}`;
    }
    if (f.size <= 0 || f.size > IMAGE_MAX_BYTES) {
      return `파일 크기는 1B ~ ${Math.floor(IMAGE_MAX_BYTES / (1024 * 1024))}MB 이내여야 합니다.`;
    }
    return null;
  }

  function pick(f: File | null) {
    setError(null);
    if (f === null) {
      setFile(null);
      return;
    }
    const v = validate(f);
    if (v) {
      setFile(null);
      setError(v);
      return;
    }
    setFile(f);
  }

  function reset() {
    setFile(null);
    setIsPrimary(false);
    setPhase('idle');
    setProgress(0);
    if (inputRef.current) inputRef.current.value = '';
  }

  function errMsg(e: unknown, fallback: string): string {
    const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
    return messageForCode(code, fallback);
  }

  async function upload() {
    if (!file || busy) return;
    setError(null);

    // Step 1 — mint the presigned URL.
    let presigned;
    try {
      setPhase('requesting');
      presigned = await createUrl.mutateAsync({
        productId,
        contentType: file.type,
        contentLength: file.size,
      });
    } catch (e) {
      setPhase('idle');
      setError(errMsg(e, '업로드 URL 발급에 실패했습니다.'));
      return;
    }

    // Step 2 — DIRECT browser → S3 PUT (no credential).
    try {
      setPhase('uploading');
      setProgress(0);
      await uploadToPresignedUrl(presigned.uploadUrl, file, setProgress);
    } catch (e) {
      setPhase('idle');
      setProgress(0);
      const status = e instanceof PresignedUploadError ? e.status : 0;
      setError(
        status === 0
          ? '파일 업로드 중 네트워크 오류가 발생했습니다. 다시 시도하세요.'
          : `파일 업로드에 실패했습니다 (HTTP ${status}). 다시 시도하세요.`,
      );
      return;
    }

    // Step 3 — register the uploaded objectKey.
    try {
      setPhase('registering');
      await register.mutateAsync({
        productId,
        body: {
          objectKey: presigned.objectKey,
          sortOrder: nextSortOrder,
          isPrimary,
        },
      });
    } catch (e) {
      setPhase('idle');
      setError(errMsg(e, '이미지 등록에 실패했습니다. 다시 시도하세요.'));
      return;
    }

    reset();
    onUploaded?.();
  }

  return (
    <div
      className="rounded-md border border-dashed border-border p-3"
      data-testid="image-upload-field"
    >
      <p className="mb-2 text-sm font-medium text-foreground">이미지 추가</p>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <input
          ref={inputRef}
          id={fileId}
          type="file"
          accept={ACCEPT}
          disabled={disabled || busy}
          onChange={(e) => pick(e.target.files?.[0] ?? null)}
          className="text-sm"
          aria-label="이미지 파일 선택"
          data-testid="image-upload-input"
        />
        <label
          htmlFor={primaryId}
          className="flex items-center gap-1 text-sm text-foreground"
        >
          <input
            id={primaryId}
            type="checkbox"
            checked={isPrimary}
            disabled={disabled || busy}
            onChange={(e) => setIsPrimary(e.target.checked)}
            data-testid="image-upload-primary"
          />
          대표 이미지
        </label>
        <Button
          size="sm"
          onClick={upload}
          disabled={disabled || busy || file === null}
          data-testid="image-upload-submit"
        >
          {phase === 'requesting'
            ? 'URL 발급 중…'
            : phase === 'uploading'
              ? '업로드 중…'
              : phase === 'registering'
                ? '등록 중…'
                : '업로드'}
        </Button>
      </div>

      {phase === 'uploading' && (
        <div
          className="mt-2 h-2 w-full overflow-hidden rounded bg-muted"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(progress * 100)}
          data-testid="image-upload-progress"
        >
          <div
            className="h-full bg-primary transition-[width]"
            style={{ width: `${Math.round(progress * 100)}%` }}
          />
        </div>
      )}

      {disabled && (
        <p
          className="mt-2 text-xs text-muted-foreground"
          data-testid="image-upload-limit"
        >
          이미지는 상품당 최대 개수까지만 등록할 수 있습니다.
        </p>
      )}

      {error && (
        <p
          role="alert"
          className="mt-2 text-xs text-destructive"
          data-testid="image-upload-error"
        >
          {error}
        </p>
      )}
    </div>
  );
}
