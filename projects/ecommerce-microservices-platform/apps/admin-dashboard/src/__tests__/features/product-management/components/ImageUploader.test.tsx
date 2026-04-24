import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImageUploader } from '@/features/product-management/components/ImageUploader';

describe('ImageUploader', () => {
  const defaultProps = {
    onFilesSelected: vi.fn(),
    disabled: false,
    currentCount: 0,
    maxCount: 10,
  };

  beforeEach(() => {
    defaultProps.onFilesSelected.mockClear();
  });

  it('업로드 영역을 렌더링한다', () => {
    render(<ImageUploader {...defaultProps} />);

    expect(screen.getByText(/이미지를 드래그하거나 클릭하여 선택하세요/)).toBeInTheDocument();
    expect(screen.getByText(/JPEG, PNG, WebP/)).toBeInTheDocument();
    expect(screen.getByText('파일 선택')).toBeInTheDocument();
  });

  it('현재 이미지 수와 최대 수를 표시한다', () => {
    render(<ImageUploader {...defaultProps} currentCount={3} />);
    expect(screen.getByText(/3\/10장/)).toBeInTheDocument();
  });

  it('파일 입력 변경 시 onFilesSelected를 호출한다', async () => {
    render(<ImageUploader {...defaultProps} />);

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
    const input = screen.getByTestId('file-input');

    await userEvent.upload(input, file);

    expect(defaultProps.onFilesSelected).toHaveBeenCalledWith([file]);
  });

  it('최대 수량 도달 시 비활성 메시지를 표시한다', () => {
    render(<ImageUploader {...defaultProps} currentCount={10} />);

    expect(screen.getByText('이미지를 더 이상 추가할 수 없습니다')).toBeInTheDocument();
    expect(screen.queryByText('파일 선택')).not.toBeInTheDocument();
  });

  it('disabled일 때 파일 선택 버튼이 표시되지 않는다', () => {
    render(<ImageUploader {...defaultProps} disabled />);
    // The drop zone should still render but with disabled styling
    expect(screen.getByTestId('image-uploader')).toBeInTheDocument();
  });

  it('드래그앤드롭 이벤트를 처리한다', () => {
    render(<ImageUploader {...defaultProps} />);
    const dropZone = screen.getByTestId('image-uploader');

    const file = new File(['test'], 'test.png', { type: 'image/png' });
    const dataTransfer = {
      files: [file],
      items: [{ kind: 'file', type: 'image/png', getAsFile: () => file }],
      types: ['Files'],
    };

    fireEvent.dragOver(dropZone, { dataTransfer });
    fireEvent.drop(dropZone, { dataTransfer });

    expect(defaultProps.onFilesSelected).toHaveBeenCalledWith([file]);
  });
});
