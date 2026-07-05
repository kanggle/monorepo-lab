'use client';

interface ImageUploadProgressProps {
  /** Upload completion fraction in the [0, 1] range. */
  progress: number;
}

/**
 * Presigned upload progress bar (TASK-PC-FE-200 — extracted from
 * {@link ImageUploadField}, presentational only). The upload orchestration
 * (state, XHR progress wiring, phase transitions) stays owned by
 * `ImageUploadField`; the `image-upload-progress` testid + ARIA are unchanged.
 */
export function ImageUploadProgress({ progress }: ImageUploadProgressProps) {
  return (
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
  );
}
