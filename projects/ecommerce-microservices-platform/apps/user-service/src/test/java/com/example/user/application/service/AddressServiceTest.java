package com.example.user.application.service;

import com.example.user.application.command.CreateAddressCommand;
import com.example.user.application.command.UpdateAddressCommand;
import com.example.user.application.result.AddressResult;
import com.example.user.domain.exception.AddressLimitExceededException;
import com.example.user.domain.exception.AddressNotFoundException;
import com.example.user.domain.exception.DefaultAddressCannotBeDeletedException;
import com.example.user.domain.model.Address;
import com.example.user.domain.repository.AddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService 단위 테스트")
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    private static final UUID USER_ID = UUID.randomUUID();

    private Address createAddress(UUID userId, String label, boolean isDefault) {
        return Address.create(userId, label, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, isDefault);
    }

    @Nested
    @DisplayName("getAddresses")
    class GetAddresses {

        @Test
        @DisplayName("사용자의 전체 주소 목록을 반환한다")
        void getAddresses_existingUser_returnsAddresses() {
            Address addr1 = createAddress(USER_ID, "집", true);
            Address addr2 = createAddress(USER_ID, "회사", false);
            given(addressRepository.findAllByUserId(USER_ID)).willReturn(List.of(addr1, addr2));

            List<AddressResult> results = addressService.getAddresses(USER_ID);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).label()).isEqualTo("집");
            assertThat(results.get(1).label()).isEqualTo("회사");
        }

        @Test
        @DisplayName("주소가 없으면 빈 목록을 반환한다")
        void getAddresses_noAddresses_returnsEmptyList() {
            given(addressRepository.findAllByUserId(USER_ID)).willReturn(List.of());

            List<AddressResult> results = addressService.getAddresses(USER_ID);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("createAddress")
    class CreateAddress {

        @Test
        @DisplayName("첫 번째 주소는 자동으로 기본 주소가 된다")
        void createAddress_firstAddress_automaticallyDefault() {
            given(addressRepository.countByUserId(USER_ID)).willReturn(0);
            given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new CreateAddressCommand(USER_ID, "집", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, false);

            UUID id = addressService.createAddress(command);

            assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("isDefault=true로 생성 시 기존 기본 주소가 해제된다")
        void createAddress_isDefaultTrue_unmarksExistingDefault() {
            given(addressRepository.countByUserId(USER_ID)).willReturn(1);
            given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new CreateAddressCommand(USER_ID, "새주소", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, true);

            addressService.createAddress(command);

            then(addressRepository).should().unmarkDefaultByUserId(USER_ID);
        }

        @Test
        @DisplayName("isDefault=false로 생성 시 기존 기본 주소가 유지된다")
        void createAddress_isDefaultFalse_keepsExistingDefault() {
            given(addressRepository.countByUserId(USER_ID)).willReturn(1);
            given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new CreateAddressCommand(USER_ID, "새주소", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, false);

            addressService.createAddress(command);

            then(addressRepository).should(never()).unmarkDefaultByUserId(any());
        }

        @Test
        @DisplayName("10개 초과 시 AddressLimitExceededException이 발생한다")
        void createAddress_exceeds10_throwsLimitExceeded() {
            given(addressRepository.countByUserId(USER_ID)).willReturn(10);

            var command = new CreateAddressCommand(USER_ID, "새주소", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", null, false);

            assertThatThrownBy(() -> addressService.createAddress(command))
                    .isInstanceOf(AddressLimitExceededException.class);
        }
    }

    @Nested
    @DisplayName("updateAddress")
    class UpdateAddress {

        @Test
        @DisplayName("주소 필드를 부분 수정한다")
        void updateAddress_partialUpdate_updatesFields() {
            Address address = createAddress(USER_ID, "집", false);
            given(addressRepository.findByIdAndUserId(address.getId(), USER_ID))
                    .willReturn(Optional.of(address));

            var command = new UpdateAddressCommand(USER_ID, address.getId(),
                    "새이름", null, null, null, null, null, null);

            AddressResult result = addressService.updateAddress(command);

            assertThat(result.label()).isEqualTo("새이름");
        }

        @Test
        @DisplayName("isDefault=true로 변경 시 기존 기본 주소가 벌크 쿼리로 해제된다")
        void updateAddress_setDefault_unmarksExisting() {
            Address target = createAddress(USER_ID, "대상", false);
            given(addressRepository.findByIdAndUserId(target.getId(), USER_ID))
                    .willReturn(Optional.of(target));

            var command = new UpdateAddressCommand(USER_ID, target.getId(),
                    null, null, null, null, null, null, true);

            AddressResult result = addressService.updateAddress(command);

            assertThat(result.isDefault()).isTrue();
            then(addressRepository).should().unmarkDefaultByUserId(USER_ID);
        }

        @Test
        @DisplayName("유일한 주소의 isDefault를 false로 변경 시도하면 true가 유지된다")
        void updateAddress_onlyAddressSetDefaultFalse_keepsTrue() {
            Address onlyAddress = createAddress(USER_ID, "유일", true);
            given(addressRepository.findByIdAndUserId(onlyAddress.getId(), USER_ID))
                    .willReturn(Optional.of(onlyAddress));
            given(addressRepository.findAllByUserId(USER_ID)).willReturn(List.of(onlyAddress));

            var command = new UpdateAddressCommand(USER_ID, onlyAddress.getId(),
                    null, null, null, null, null, null, false);

            AddressResult result = addressService.updateAddress(command);

            assertThat(result.isDefault()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 주소 수정 시 AddressNotFoundException이 발생한다")
        void updateAddress_nonExisting_throws() {
            UUID addressId = UUID.randomUUID();
            given(addressRepository.findByIdAndUserId(addressId, USER_ID))
                    .willReturn(Optional.empty());

            var command = new UpdateAddressCommand(USER_ID, addressId,
                    "이름", null, null, null, null, null, null);

            assertThatThrownBy(() -> addressService.updateAddress(command))
                    .isInstanceOf(AddressNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteAddress")
    class DeleteAddress {

        @Test
        @DisplayName("비기본 주소를 삭제한다")
        void deleteAddress_nonDefault_deletes() {
            Address address = createAddress(USER_ID, "삭제대상", false);
            given(addressRepository.findByIdAndUserId(address.getId(), USER_ID))
                    .willReturn(Optional.of(address));

            addressService.deleteAddress(USER_ID, address.getId());

            then(addressRepository).should().delete(address);
        }

        @Test
        @DisplayName("기본 주소이면서 유일한 주소이면 삭제를 허용한다")
        void deleteAddress_defaultAndOnly_deletes() {
            Address address = createAddress(USER_ID, "유일기본", true);
            given(addressRepository.findByIdAndUserId(address.getId(), USER_ID))
                    .willReturn(Optional.of(address));
            given(addressRepository.countByUserId(USER_ID)).willReturn(1);

            addressService.deleteAddress(USER_ID, address.getId());

            then(addressRepository).should().delete(address);
        }

        @Test
        @DisplayName("기본 주소 삭제 시 다른 주소가 있으면 예외가 발생한다")
        void deleteAddress_defaultWithOthers_throws() {
            Address defaultAddr = createAddress(USER_ID, "기본", true);
            given(addressRepository.findByIdAndUserId(defaultAddr.getId(), USER_ID))
                    .willReturn(Optional.of(defaultAddr));
            given(addressRepository.countByUserId(USER_ID)).willReturn(2);

            assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, defaultAddr.getId()))
                    .isInstanceOf(DefaultAddressCannotBeDeletedException.class);

            then(addressRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("기본 주소 삭제 시 countByUserId()로 다른 주소 존재 여부를 확인한다")
        void deleteAddress_default_usesCountQuery() {
            Address address = createAddress(USER_ID, "기본", true);
            given(addressRepository.findByIdAndUserId(address.getId(), USER_ID))
                    .willReturn(Optional.of(address));
            given(addressRepository.countByUserId(USER_ID)).willReturn(1);

            addressService.deleteAddress(USER_ID, address.getId());

            then(addressRepository).should().countByUserId(USER_ID);
            then(addressRepository).should(never()).findAllByUserId(any());
        }

        @Test
        @DisplayName("존재하지 않는 주소 삭제 시 AddressNotFoundException이 발생한다")
        void deleteAddress_nonExisting_throws() {
            UUID addressId = UUID.randomUUID();
            given(addressRepository.findByIdAndUserId(addressId, USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, addressId))
                    .isInstanceOf(AddressNotFoundException.class);
        }
    }
}
