import { describe, it, expect } from 'vitest';
import { deviceLabelFromUserAgent } from '@/features/notification/lib/device-label';

describe('deviceLabelFromUserAgent (TASK-FE-085)', () => {
  it('Windows Chrome UA → "Windows · Chrome"', () => {
    const ua =
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
    expect(deviceLabelFromUserAgent(ua)).toBe('Windows · Chrome');
  });

  it('Android Chrome UA → "Android · Chrome"', () => {
    const ua =
      'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
    expect(deviceLabelFromUserAgent(ua)).toBe('Android · Chrome');
  });

  it('iOS Safari UA → "iOS · Safari"', () => {
    const ua =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
    expect(deviceLabelFromUserAgent(ua)).toBe('iOS · Safari');
  });

  it('Edge UA is detected as Edge, not Chrome', () => {
    const ua =
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0';
    expect(deviceLabelFromUserAgent(ua)).toBe('Windows · Edge');
  });

  it('macOS Firefox UA → "macOS · Firefox"', () => {
    const ua = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0';
    expect(deviceLabelFromUserAgent(ua)).toBe('macOS · Firefox');
  });

  it('null / empty / unrecognized UA → fallback label (never throws)', () => {
    expect(deviceLabelFromUserAgent(null)).toBe('알 수 없는 기기');
    expect(deviceLabelFromUserAgent(undefined)).toBe('알 수 없는 기기');
    expect(deviceLabelFromUserAgent('')).toBe('알 수 없는 기기');
    expect(deviceLabelFromUserAgent('   ')).toBe('알 수 없는 기기');
    expect(deviceLabelFromUserAgent('totally-unknown-agent')).toBe('알 수 없는 기기');
  });
});
