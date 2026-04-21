export interface EditingState {
  variantId: string;
  optionName: string;
  additionalPrice: number;
}

export interface AddingState {
  optionName: string;
  stock: number;
  additionalPrice: number;
}
