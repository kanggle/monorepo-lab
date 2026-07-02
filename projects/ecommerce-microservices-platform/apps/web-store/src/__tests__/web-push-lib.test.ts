import { describe, it, expect } from 'vitest';
import { urlBase64ToUint8Array, isPushSupported } from '@/features/notification/lib/web-push';

describe('web-push lib', () => {
  describe('urlBase64ToUint8Array', () => {
    it('decodes a base64url string (with padding restored) to bytes', () => {
      // "SGVsbG8" is base64url for "Hello" (no padding)
      const bytes = urlBase64ToUint8Array('SGVsbG8');
      expect(Array.from(bytes)).toEqual([72, 101, 108, 108, 111]);
    });

    it('handles base64url -/_ characters', () => {
      // bytes [255, 255, 254] → standard base64 "///+" → base64url "___-"
      const bytes = urlBase64ToUint8Array('___-');
      expect(Array.from(bytes)).toEqual([255, 255, 254]);
    });
  });

  describe('isPushSupported', () => {
    it('returns false in a jsdom environment lacking PushManager/serviceWorker', () => {
      expect(isPushSupported()).toBe(false);
    });
  });
});
