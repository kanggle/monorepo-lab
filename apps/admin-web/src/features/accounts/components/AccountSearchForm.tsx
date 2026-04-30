'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import { AccountSearchSchema, type AccountSearchInput } from '../schemas';
import { useAccountSearch } from '../hooks/useAccountSearch';
import { useAccountList } from '../hooks/useAccountList';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { Table, THead, TBody, TR, TH, TD } from '@/shared/ui/table';
import { Badge } from '@/shared/ui/badge';
import type { AccountSummary } from '@/shared/api/admin-api';

const PAGE_SIZE = 20;

interface Props {
  isSuperAdmin?: boolean;
}

export function AccountSearchForm({ isSuperAdmin = false }: Props) {
  const [email, setEmail] = useState<string | undefined>();
  const [page, setPage] = useState(0);

  const form = useForm<AccountSearchInput>({
    resolver: zodResolver(AccountSearchSchema),
    defaultValues: { email: '' },
    mode: 'onBlur',
  });

  // email 입력 후 검색 시 기존 useAccountSearch 사용
  const searchQuery = useAccountSearch(email);

  // SUPER_ADMIN이고 이메일 미입력 시 전체 목록 조회
  const listQuery = useAccountList(page, PAGE_SIZE, isSuperAdmin && !email);

  const isEmailMode = Boolean(email);
  // 검색 중 메시지는 사용자가 검색 버튼을 눌렀을 때만 표시
  const isSearching = isEmailMode && searchQuery.isLoading;
  const isError = isEmailMode ? searchQuery.isError : listQuery.isError;

  const rows: AccountSummary[] = isEmailMode
    ? (searchQuery.data ?? [])
    : (listQuery.data?.content ?? []);
  const totalPages = isEmailMode ? 1 : (listQuery.data?.totalPages ?? 0);
  const isEmpty = !listQuery.isLoading && !isSearching && !isError && rows.length === 0;

  function handleSearch(v: AccountSearchInput) {
    setEmail(v.email || undefined);
    setPage(0);
  }

  return (
    <div className="flex flex-col gap-6">
      <form
        aria-label="account-search"
        onSubmit={form.handleSubmit(handleSearch)}
        className="flex flex-col gap-1"
        noValidate
      >
        <div className="flex items-end gap-2">
          <div className="flex flex-col gap-1">
            <Label htmlFor="search-email">이메일</Label>
            <Input id="search-email" type="email" {...form.register('email')} />
          </div>
          <Button type="submit">검색</Button>
        </div>
        {form.formState.errors.email ? (
          <p role="alert" className="text-xs text-destructive">
            {form.formState.errors.email.message}
          </p>
        ) : null}
        {isSearching ? <p className="text-xs text-muted-foreground">검색 중...</p> : null}
        {isError ? (
          <p role="alert" className="text-xs text-destructive">
            조회에 실패했습니다.
          </p>
        ) : null}
        {isEmpty ? <p className="text-xs text-muted-foreground">결과가 없습니다.</p> : null}
      </form>

      {!isEmailMode && listQuery.isLoading ? (
        <p className="text-xs text-muted-foreground">목록을 불러오는 중...</p>
      ) : null}

      {rows.length > 0 ? (
        <>
          <Table>
            <THead>
              <TR>
                <TH>ID</TH>
                <TH>이메일</TH>
                <TH>상태</TH>
                <TH>가입일</TH>
                <TH />
              </TR>
            </THead>
            <TBody>
              {rows.map((a) => (
                <TR key={a.id}>
                  <TD>{a.id}</TD>
                  <TD>{a.email}</TD>
                  <TD><Badge>{a.status}</Badge></TD>
                  <TD>{a.createdAt}</TD>
                  <TD>
                    <Link className="text-primary underline" href={`/accounts/${a.id}`}>
                      상세
                    </Link>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>

          {!isEmailMode && totalPages > 1 ? (
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </Button>
              <span className="text-sm text-muted-foreground">
                {page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
