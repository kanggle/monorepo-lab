'use client';

import { ApiError, messageForCode } from '@/shared/api/errors';
import { useChangeOwnPassword, useUpdateOwnProfile } from '../hooks/use-operators';
import { ChangePasswordForm } from './ChangePasswordForm';
import { MyProfileForm } from './MyProfileForm';

/**
 * 계정 설정 셀프서비스 (TASK-PC-FE-045). The LOGGED-IN operator's own
 * self-service mutations — change-password (`me/password`) + default finance
 * account (`me/profile`). Moved here from {@code OperatorsScreen} so that
 * "내 것"(계정 설정) is separated from "남 관리"(운영자 관리), matching the
 * AWS/GCP console split (account menu = self, IAM/Users = managing others).
 *
 * Client component (React Query hooks) rendered by the server-side
 * {@code /account} page under the console QueryClient provider. The API /
 * proxy / endpoints are unchanged — only the UI surface moved.
 */

export interface AccountSelfServiceProps {
  /** Server-rendered initial default-finance-account id (null when never set). */
  initialDefaultAccountId?: string | null;
}

export function AccountSelfService({
  initialDefaultAccountId = null,
}: AccountSelfServiceProps) {
  const changePw = useChangeOwnPassword();
  const updateProfile = useUpdateOwnProfile();

  const pwError =
    changePw.error instanceof ApiError
      ? messageForCode((changePw.error as ApiError).code, changePw.error.message)
      : changePw.error
        ? '비밀번호 변경에 실패했습니다.'
        : null;

  const updateProfileError =
    updateProfile.error instanceof ApiError
      ? messageForCode(
          (updateProfile.error as ApiError).code,
          updateProfile.error.message,
        )
      : updateProfile.error
        ? '프로파일 저장에 실패했습니다.'
        : null;

  return (
    <div data-testid="account-self-service">
      <ChangePasswordForm
        onSubmit={(currentPassword, newPassword) =>
          changePw.mutate({ currentPassword, newPassword })
        }
        serverError={pwError}
        pending={changePw.isPending}
        succeeded={changePw.isSuccess}
      />
      <MyProfileForm
        initial={initialDefaultAccountId}
        onSubmit={(defaultAccountId) =>
          updateProfile.mutate({ defaultAccountId })
        }
        serverError={updateProfileError}
        pending={updateProfile.isPending}
        succeeded={updateProfile.isSuccess}
      />
    </div>
  );
}
