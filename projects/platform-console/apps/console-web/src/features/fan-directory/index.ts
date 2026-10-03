/**
 * fan-directory feature (TASK-MONO-751 — ADR-MONO-079 D4-A): the platform operator's fan
 * DIRECTORY screens — agencies · artists · artist groups — over artist-service. Directory
 * only: fan community, membership and notifications stay closed to operators (ADR-MONO-059).
 */
export {
  getAgenciesSectionState,
  getAgencyDetailSectionState,
  getArtistsSectionState,
  getArtistDetailSectionState,
  getGroupDetailSectionState,
} from './api/fan-state';
export type { FanSectionState } from './api/fan-state';
export { AgenciesScreen } from './components/AgenciesScreen';
export { AgencyCreateForm } from './components/AgencyCreateForm';
export { AgencyDetail } from './components/AgencyDetail';
export { ArtistsScreen } from './components/ArtistsScreen';
export { ArtistCreateForm } from './components/ArtistCreateForm';
export { ArtistDetail } from './components/ArtistDetail';
export { GroupsScreen } from './components/GroupsScreen';
export { GroupDetail } from './components/GroupDetail';
export { FanSectionNote } from './components/FanSectionNote';
export { decodeSegment } from './lib/decode-segment';
