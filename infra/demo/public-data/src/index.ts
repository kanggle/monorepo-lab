// DEMO-PUBLIC-DATA: `@demo/public-data` 의 공개 표면.
//
// 🔴 **발행자(`bin/publish-public-data.mjs`)는 여기서 재수출하지 않는다.** 그쪽만
//    `@vercel/blob` 을 임포트하고, 그 의존은 CLI 에만 있다. 여기서 재수출하면 세 Next 앱이
//    전부 그 패키지를 설치해야 하고, 그 순간 «판독자는 의존성이 없다» 는 성질이 사라진다.
//    발행자는 `src/contract.mjs` 를 **직접** 임포트한다(같은 런타임, 다른 진입점).

export {
  PUBLIC_DATA_SCHEMA_VERSION,
  PUBLIC_DATASETS,
  PUBLIC_DATA_SOURCES,
  COLLECTION_STATUSES,
  pointerPath,
  versionPath,
  imagePath,
  makeDataVersion,
  validateEnvelopeShape,
  validatePointerShape,
  validateDatasetData,
  validateEnvelope,
  type PublicDataSource,
  type CollectionStatus,
  type PublicDataEnvelope,
  type PublicDataPointer,
  type ValidationResult,
} from './contract.mjs';

export type {
  PublicArtist,
  PublicPost,
  PublicMembershipPlan,
  FanPublicData,
  PublicProduct,
  PublicProductImage,
  PublicProductOption,
  PublicCategory,
  StorePublicData,
  ConsoleSampleData,
  ConsoleSampleDomain,
  ConsoleSampleTable,
  ConsoleSampleColumn,
  ConsoleSampleMetric,
  PublicDataByDataset,
  PublicDataset,
} from './datasets';

export {
  createPublicDataReader,
  bundledEnvelope,
  __resetPublicDataCache,
  type PublicDataResult,
} from './read';

export {
  paginate,
  normalizeQuery,
  queryProducts,
  queryArtists,
  categoryFacets,
  type PageRequest,
  type PagedResult,
  type StoreQuery,
  type StoreSortOrder,
  type ArtistQuery,
} from './query';
