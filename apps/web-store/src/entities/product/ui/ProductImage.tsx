'use client';

import Image from 'next/image';
import { useCallback, useState } from 'react';
import styles from './ProductImage.module.css';

interface ProductImageProps {
  images: string[];
  alt: string;
}

export function ProductImage({ images, alt }: ProductImageProps) {
  const [current, setCurrent] = useState(0);
  const [errorSet, setErrorSet] = useState<Set<number>>(new Set());

  const total = images.length;
  const hasMultiple = total > 1;

  const prev = useCallback(() => {
    setCurrent((i) => (i - 1 + total) % total);
  }, [total]);

  const next = useCallback(() => {
    setCurrent((i) => (i + 1) % total);
  }, [total]);

  function handleError(index: number) {
    setErrorSet((prev) => new Set(prev).add(index));
  }

  return (
    <div className={styles.imageGallery}>
      <div className={styles.imageWrapper}>
        {errorSet.has(current) ? (
          <div className={styles.imageFallback}>이미지를 불러올 수 없습니다</div>
        ) : (
          <Image
            src={images[current]}
            alt={`${alt} ${current + 1}`}
            fill
            sizes="480px"
            className={styles.imageEl}
            onError={() => handleError(current)}
            priority={current === 0}
            unoptimized={images[current].includes('placehold.co') || images[current].startsWith('http://localhost')}
          />
        )}

        {hasMultiple && (
          <>
            <button
              type="button"
              className={`${styles.slideBtn} ${styles.slideBtnPrev}`}
              onClick={prev}
              aria-label="이전 이미지"
            >
              ‹
            </button>
            <button
              type="button"
              className={`${styles.slideBtn} ${styles.slideBtnNext}`}
              onClick={next}
              aria-label="다음 이미지"
            >
              ›
            </button>
          </>
        )}

        {hasMultiple && (
          <div className={styles.dots}>
            {images.map((_, i) => (
              <button
                key={i}
                type="button"
                className={`${styles.dot} ${i === current ? styles.dotActive : ''}`}
                onClick={() => setCurrent(i)}
                aria-label={`이미지 ${i + 1}`}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
