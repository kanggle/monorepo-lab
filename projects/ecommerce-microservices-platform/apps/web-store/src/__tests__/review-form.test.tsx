import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReviewForm } from '@/features/review/ui/ReviewForm';

describe('ReviewForm', () => {
  let onSubmit: ReturnType<typeof vi.fn>;
  let onCancel: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onSubmit = vi.fn().mockResolvedValue(undefined);
    onCancel = vi.fn();
  });

  it('별점, 제목, 내용 입력 필드를 렌더링한다', () => {
    render(<ReviewForm onSubmit={onSubmit} />);

    expect(screen.getByLabelText('제목')).toBeInTheDocument();
    expect(screen.getByLabelText('내용')).toBeInTheDocument();
    expect(screen.getByText('별점을 선택해주세요')).toBeInTheDocument();
  });

  it('모든 필드가 비어있으면 제출 버튼이 비활성화된다', () => {
    render(<ReviewForm onSubmit={onSubmit} />);

    expect(screen.getByRole('button', { name: '리뷰 작성' })).toBeDisabled();
  });

  it('모든 필드를 입력하면 제출 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    render(<ReviewForm onSubmit={onSubmit} />);

    // Select 5-star rating
    await user.click(screen.getByRole('radio', { name: '5점' }));
    await user.type(screen.getByLabelText('제목'), '좋은 상품');
    await user.type(screen.getByLabelText('내용'), '정말 만족합니다.');

    expect(screen.getByRole('button', { name: '리뷰 작성' })).toBeEnabled();
  });

  it('폼 제출 시 onSubmit이 올바른 데이터로 호출된다', async () => {
    const user = userEvent.setup();
    render(<ReviewForm onSubmit={onSubmit} />);

    await user.click(screen.getByRole('radio', { name: '4점' }));
    await user.type(screen.getByLabelText('제목'), '괜찮은 상품');
    await user.type(screen.getByLabelText('내용'), '가격 대비 괜찮습니다.');
    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        rating: 4,
        title: '괜찮은 상품',
        content: '가격 대비 괜찮습니다.',
      });
    });
  });

  it('취소 버튼 클릭 시 onCancel이 호출된다', async () => {
    const user = userEvent.setup();
    render(<ReviewForm onSubmit={onSubmit} onCancel={onCancel} />);

    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(onCancel).toHaveBeenCalled();
  });

  it('취소 버튼이 없으면 onCancel이 제공되지 않아도 된다', () => {
    render(<ReviewForm onSubmit={onSubmit} />);

    expect(screen.queryByRole('button', { name: '취소' })).not.toBeInTheDocument();
  });

  it('제출 중일 때 버튼이 비활성화되고 텍스트가 변경된다', () => {
    render(<ReviewForm onSubmit={onSubmit} isPending />);

    expect(screen.getByRole('button', { name: '저장 중...' })).toBeDisabled();
  });

  it('수정 모드에서 초기값이 표시된다', () => {
    render(
      <ReviewForm
        initialRating={4}
        initialTitle="기존 제목"
        initialContent="기존 내용"
        onSubmit={onSubmit}
        submitLabel="리뷰 수정"
      />,
    );

    expect(screen.getByLabelText('제목')).toHaveValue('기존 제목');
    expect(screen.getByLabelText('내용')).toHaveValue('기존 내용');
    expect(screen.getByRole('button', { name: '리뷰 수정' })).toBeEnabled();
  });

  it('제출 실패 시 에러 메시지를 표시한다', async () => {
    onSubmit.mockRejectedValueOnce(new Error('작성 권한이 없습니다.'));

    const user = userEvent.setup();
    render(<ReviewForm onSubmit={onSubmit} />);

    await user.click(screen.getByRole('radio', { name: '5점' }));
    await user.type(screen.getByLabelText('제목'), '좋아요');
    await user.type(screen.getByLabelText('내용'), '아주 좋습니다.');
    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    await waitFor(() => {
      expect(screen.getByText('작성 권한이 없습니다.')).toBeInTheDocument();
    });
  });

  it('409 중복 리뷰 에러 시 전용 메시지를 표시한다', async () => {
    onSubmit.mockRejectedValueOnce({
      code: 'REVIEW_ALREADY_EXISTS',
      message: 'User already reviewed this product',
      timestamp: new Date().toISOString(),
    });

    const user = userEvent.setup();
    render(<ReviewForm onSubmit={onSubmit} />);

    await user.click(screen.getByRole('radio', { name: '5점' }));
    await user.type(screen.getByLabelText('제목'), '좋아요');
    await user.type(screen.getByLabelText('내용'), '아주 좋습니다.');
    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    await waitFor(() => {
      expect(screen.getByText('이미 이 상품에 리뷰를 작성했습니다.')).toBeInTheDocument();
    });
  });

  it('422 PRODUCT_NOT_PURCHASED 에러 시 구매 필요 메시지를 표시한다', async () => {
    onSubmit.mockRejectedValueOnce({
      code: 'PRODUCT_NOT_PURCHASED',
      message: 'User has not purchased this product',
      timestamp: new Date().toISOString(),
    });

    const user = userEvent.setup();
    render(<ReviewForm onSubmit={onSubmit} />);

    await user.click(screen.getByRole('radio', { name: '5점' }));
    await user.type(screen.getByLabelText('제목'), '좋아요');
    await user.type(screen.getByLabelText('내용'), '아주 좋습니다.');
    await user.click(screen.getByRole('button', { name: '리뷰 작성' }));

    await waitFor(() => {
      expect(screen.getByText('구매한 상품에만 리뷰를 작성할 수 있습니다.')).toBeInTheDocument();
    });
  });
});
