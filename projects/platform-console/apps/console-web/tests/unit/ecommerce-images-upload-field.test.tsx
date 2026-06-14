import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

/**
 * `ImageUploadField` orchestration (TASK-PC-FE-082 AC-2) — the presigned
 * three-step ORDER is the contract:
 *   1. mint upload URL (#11)  → 2. DIRECT browser→S3 PUT  → 3. register (#12).
 * Step 3 MUST carry the `objectKey` returned by step 1, and MUST NOT run if
 * the PUT (step 2) fails (no orphan registration).
 */

// vi.mock is hoisted above module-level declarations, so the spies + the call
// log MUST be created inside vi.hoisted (else the factory references an
// uninitialised binding — "Cannot access 'uploadMock' before initialization").
const { calls, createUploadUrlMock, uploadMock, registerMock, FakePresignedUploadError } =
  vi.hoisted(() => {
    const calls: string[] = [];
    class FakePresignedUploadError extends Error {
      status: number;
      constructor(status: number, message: string) {
        super(message);
        this.status = status;
      }
    }
    return {
      calls,
      createUploadUrlMock: vi.fn(async () => {
        calls.push('createUploadUrl');
        return {
          uploadUrl: 'http://minio.local/PUT?sig',
          objectKey: 'products/p-1/0-x.png',
        };
      }),
      uploadMock: vi.fn(async () => {
        calls.push('upload');
      }),
      registerMock: vi.fn(async () => {
        calls.push('register');
        return {};
      }),
      FakePresignedUploadError,
    };
  });

vi.mock('@/features/ecommerce-ops/hooks/use-ecommerce-images', () => ({
  useCreateUploadUrl: () => ({ mutateAsync: createUploadUrlMock }),
  useRegisterImage: () => ({ mutateAsync: registerMock }),
  uploadToPresignedUrl: uploadMock,
  PresignedUploadError: FakePresignedUploadError,
}));

import { ImageUploadField } from '@/features/ecommerce-ops/components/ImageUploadField';

function selectFile() {
  const input = screen.getByTestId('image-upload-input') as HTMLInputElement;
  const file = new File([new Uint8Array(2048)], 'photo.png', { type: 'image/png' });
  fireEvent.change(input, { target: { files: [file] } });
  return file;
}

beforeEach(() => {
  calls.length = 0;
  createUploadUrlMock.mockClear();
  createUploadUrlMock.mockImplementation(async () => {
    calls.push('createUploadUrl');
    return {
      uploadUrl: 'http://minio.local/PUT?sig',
      objectKey: 'products/p-1/0-x.png',
    };
  });
  uploadMock.mockClear();
  uploadMock.mockImplementation(async () => {
    calls.push('upload');
  });
  registerMock.mockClear();
  registerMock.mockImplementation(async () => {
    calls.push('register');
    return {};
  });
});

describe('ImageUploadField — presigned 3-step order', () => {
  it('runs upload-url → S3 PUT → register IN ORDER, carrying the objectKey + sortOrder', async () => {
    const onUploaded = vi.fn();
    render(
      <ImageUploadField productId="p-1" nextSortOrder={3} onUploaded={onUploaded} />,
    );

    const file = selectFile();
    fireEvent.click(screen.getByTestId('image-upload-submit'));

    await waitFor(() => expect(registerMock).toHaveBeenCalled());

    expect(calls).toEqual(['createUploadUrl', 'upload', 'register']);

    expect(createUploadUrlMock).toHaveBeenCalledWith({
      productId: 'p-1',
      contentType: 'image/png',
      contentLength: 2048,
    });
    // step 2 PUTs the file to the URL minted in step 1.
    expect(uploadMock).toHaveBeenCalledWith(
      'http://minio.local/PUT?sig',
      file,
      expect.any(Function),
    );
    // step 3 registers the objectKey from step 1 with the supplied sortOrder.
    expect(registerMock).toHaveBeenCalledWith({
      productId: 'p-1',
      body: { objectKey: 'products/p-1/0-x.png', sortOrder: 3, isPrimary: false },
    });
    await waitFor(() => expect(onUploaded).toHaveBeenCalled());
  });

  it('does NOT register when the S3 PUT fails (no orphan registration)', async () => {
    uploadMock.mockImplementationOnce(async () => {
      throw new FakePresignedUploadError(403, 'denied');
    });
    render(<ImageUploadField productId="p-1" nextSortOrder={0} />);

    selectFile();
    fireEvent.click(screen.getByTestId('image-upload-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('image-upload-error')).toBeInTheDocument(),
    );
    expect(createUploadUrlMock).toHaveBeenCalled();
    expect(uploadMock).toHaveBeenCalled();
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('rejects a disallowed content type before any network call', async () => {
    render(<ImageUploadField productId="p-1" nextSortOrder={0} />);
    const input = screen.getByTestId('image-upload-input') as HTMLInputElement;
    const bad = new File([new Uint8Array(10)], 'a.gif', { type: 'image/gif' });
    fireEvent.change(input, { target: { files: [bad] } });

    // an inline error shows + no upload-url call is made.
    expect(screen.getByTestId('image-upload-error')).toBeInTheDocument();
    expect(createUploadUrlMock).not.toHaveBeenCalled();
  });
});
