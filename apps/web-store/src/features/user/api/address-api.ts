// features/user/api/address-api: entities/user의 주소 API를 재export 하는 얇은 모듈.
// 호출자는 이 경로를 통해 CRUD 함수를 사용하며, 실제 로직(네트워크 + mock 폴백)은 entities에 위치한다.
export {
  createAddress,
  updateAddress,
  deleteAddress,
} from '@/entities/user/api/address-api';
