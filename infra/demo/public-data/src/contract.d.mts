// DEMO-PUBLIC-DATA: `contract.mjs` 의 타입 면. **런타임은 저기 한 벌뿐이다.**
//
// 🔴 이 파일에 로직을 쓰지 마라. 여기에 조건 하나라도 들어가면 그 순간 계약이 두 벌이 되고,
//    이 패키지가 존재하는 이유(발행자와 판독자가 갈라지지 않는 것)가 사라진다.
// 🔴 `contract.mjs` 에 함수를 추가하면 여기도 늘려라 — 안 늘리면 TS 쪽에서 그 함수는
//    **존재하지 않는다**(조용히. 임포트가 타입 오류로 죽는 것이 그나마 나은 쪽이다).

import type { PublicDataset, PublicDataByDataset } from './datasets.js';

export declare const PUBLIC_DATA_SCHEMA_VERSION: 1;
export declare const PUBLIC_DATASETS: readonly PublicDataset[];
export declare const PUBLIC_DATA_SOURCES: readonly PublicDataSource[];
export declare const COLLECTION_STATUSES: readonly CollectionStatus[];

/** 이 봉투의 내용물이 **어디서 왔는가.** `bundled` 는 결함이 아니라 선언된 상태다. */
export type PublicDataSource = 'backend' | 'bundled' | 'authored';

/** 컬렉션 하나의 수집 결과. 🔴 `empty` 와 `failed` 는 다른 사실이다. */
export type CollectionStatus = 'ok' | 'empty' | 'failed';

/**
 * 공개 저장본의 봉투.
 *
 * 필드 하나하나가 이 저장소가 이미 밟은 실패 하나씩에 대응한다:
 *   schemaVersion    — 못 읽는 모양을 «빈 데이터» 로 읽지 않는다.
 *   dataVersion      — 목록과 상세가 **다른 세대로 섞이지 않는다**.
 *   generatedAt      — 「지금 상태」와 「묵은 스냅샷」을 바이트로 구별한다(MONO-551 B).
 *   source / origin  — 이 숫자가 **무엇을 잰 것인지** 말한다.
 *   coverage         — 실제 수집 범위와 건수. 화면이 「이 범위 안에서」를 말할 수 있게 한다.
 *   collectionStatus — 🔴🔴 **정상적인 0건과 수집 실패를 가른다.**
 */
export interface PublicDataEnvelope<TData> {
  schemaVersion: number;
  dataset: PublicDataset;
  dataVersion: string;
  generatedAt: string;
  source: PublicDataSource;
  origin: string;
  coverage: Record<string, number>;
  collectionStatus: Record<string, CollectionStatus>;
  data: TData;
}

/**
 * `current.json` — **어느 세대가 지금인가** 만 담는다.
 *
 * ## 왜 포인터를 따로 두는가
 * 세대를 **불변 파일**로 먼저 올리고, 검증한 뒤 포인터 한 줄만 바꾼다:
 *   1. `public-data/<dataset>/v/<dataVersion>.json`  ← 불변. 덮어쓰지 않는다.
 *   2. 정합성 검증(건수·필수 필드·`failed` 없음)
 *   3. `public-data/<dataset>/current.json`          ← 이 한 줄만 교체
 * ⇒ 실패·부분 수집은 2 에서 멈추므로 **정상본을 덮어쓰지 못한다.**
 */
export interface PublicDataPointer {
  schemaVersion: number;
  dataset: PublicDataset;
  dataVersion: string;
  url: string;
  publishedAt: string;
  source: PublicDataSource;
}

export type ValidationResult = { ok: true } | { ok: false; reason: string };

export declare function pointerPath(dataset: PublicDataset | string): string;
export declare function versionPath(dataset: PublicDataset | string, dataVersion: string): string;
export declare function imagePath(sha256: string, ext: string): string;
export declare function makeDataVersion(now: Date, randomHex: string): string;

export declare function validateEnvelopeShape(
  value: unknown,
  expected: { dataset: PublicDataset | string; dataVersion?: string },
): ValidationResult;

export declare function validatePointerShape(
  value: unknown,
  expected: { dataset: PublicDataset | string },
): { ok: true; pointer: PublicDataPointer } | { ok: false; reason: string };

export declare function validateDatasetData(
  dataset: keyof PublicDataByDataset | string,
  data: unknown,
): ValidationResult;

export declare function validateEnvelope(
  value: unknown,
  expected: { dataset: PublicDataset | string; dataVersion?: string },
): ValidationResult;
