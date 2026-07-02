'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Script from 'next/script';

interface DaumPostcodeData {
  zonecode: string;
  roadAddress: string;
  jibunAddress: string;
  buildingName: string;
}

interface AddressSearchProps {
  onSelect: (data: { zipCode: string; address1: string }) => void;
}

function isDaumLoaded(): boolean {
  return typeof window !== 'undefined' && !!(window as Window & { daum?: { Postcode: unknown } }).daum?.Postcode;
}

export function AddressSearch({ onSelect }: AddressSearchProps) {
  const [open, setOpen] = useState(false);
  const [scriptLoaded, setScriptLoaded] = useState(isDaumLoaded);
  const embedRef = useRef<HTMLDivElement>(null);
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  const handleOpen = useCallback(() => {
    if (scriptLoaded || isDaumLoaded()) {
      setScriptLoaded(true);
      setOpen(true);
    }
  }, [scriptLoaded]);

  useEffect(() => {
    if (!open || !embedRef.current) return;

    const el = embedRef.current;
    el.innerHTML = '';

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    new (window as any).daum.Postcode({
      oncomplete(data: DaumPostcodeData) {
        const addr = data.roadAddress || data.jibunAddress;
        const extra = data.buildingName ? ` (${data.buildingName})` : '';
        onSelectRef.current({ zipCode: data.zonecode, address1: addr + extra });
        setOpen(false);
      },
      width: '100%',
      height: '100%',
    }).embed(el);
  }, [open]);

  return (
    <>
      <Script
        src="//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"
        strategy="afterInteractive"
        onLoad={() => setScriptLoaded(true)}
      />
      <button
        type="button"
        onClick={handleOpen}
        className="btn"
        style={{ fontSize: 'var(--font-size-sm)', whiteSpace: 'nowrap', flexShrink: 0 }}
      >
        주소 검색
      </button>

      {open && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 100,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'rgba(0,0,0,0.4)',
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) setOpen(false);
          }}
        >
          <div
            style={{
              background: 'var(--color-surface)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
              width: '100%',
              maxWidth: 500,
              height: 460,
              position: 'relative',
            }}
          >
            <button
              type="button"
              onClick={() => setOpen(false)}
              style={{
                position: 'absolute',
                top: 8,
                right: 12,
                zIndex: 1,
                background: 'var(--color-surface)',
                border: 'none',
                fontSize: 'var(--font-size-xl)',
                cursor: 'pointer',
                color: 'var(--color-text-secondary)',
              }}
              aria-label="닫기"
            >
              ✕
            </button>
            <div ref={embedRef} style={{ width: '100%', height: '100%' }} />
          </div>
        </div>
      )}
    </>
  );
}
