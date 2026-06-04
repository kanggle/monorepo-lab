import type { MasterWriteConfig } from './MasterWriteDialog';

/**
 * Per-master field configs for the generic `MasterWriteDialog` (TASK-PC-FE-048).
 * Required fields mirror the producer `masterdata-api.md` § <master> create
 * bodies; FK fields render as dropdowns sourced from the section's loaded
 * lists. `effectiveFrom` is always an optional date (producer defaults today).
 *
 * NOTE (v1 limitation): BusinessPartner's nested `paymentTerms`
 * (`{ termDays, method }`) is NOT exposed in this flat-field dialog — a
 * partner is created/updated without it (the producer accepts that). A
 * dedicated payment-terms editor is a follow-up.
 */

export const EMPLOYEE_WRITE_CONFIG: MasterWriteConfig = {
  label: '직원',
  createFields: [
    { key: 'employeeNumber', label: '사번', kind: 'text', required: true },
    { key: 'name', label: '이름', kind: 'text', required: true },
    { key: 'departmentId', label: '부서', kind: 'select', optionSource: 'departments' },
    { key: 'costCenterId', label: '비용센터', kind: 'select', optionSource: 'costCenters' },
    { key: 'jobGradeId', label: '직급', kind: 'select', optionSource: 'jobGrades' },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
  updateFields: [
    { key: 'name', label: '이름', kind: 'text' },
    { key: 'departmentId', label: '부서', kind: 'select', optionSource: 'departments' },
    { key: 'costCenterId', label: '비용센터', kind: 'select', optionSource: 'costCenters' },
    { key: 'jobGradeId', label: '직급', kind: 'select', optionSource: 'jobGrades' },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
};

export const JOB_GRADE_WRITE_CONFIG: MasterWriteConfig = {
  label: '직급',
  createFields: [
    { key: 'code', label: '코드', kind: 'text', required: true },
    { key: 'name', label: '이름', kind: 'text', required: true },
    { key: 'displayOrder', label: '표시 순서 (선택)', kind: 'number' },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
  updateFields: [
    { key: 'name', label: '이름', kind: 'text' },
    { key: 'displayOrder', label: '표시 순서', kind: 'number' },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
};

export const COST_CENTER_WRITE_CONFIG: MasterWriteConfig = {
  label: '비용센터',
  createFields: [
    { key: 'code', label: '코드', kind: 'text', required: true },
    { key: 'name', label: '이름', kind: 'text', required: true },
    { key: 'departmentId', label: '부서', kind: 'select', optionSource: 'departments' },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
  updateFields: [
    { key: 'name', label: '이름', kind: 'text' },
    { key: 'departmentId', label: '부서', kind: 'select', optionSource: 'departments' },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
};

const PARTNER_TYPE_OPTIONS = [
  { value: 'CUSTOMER', label: 'CUSTOMER (고객)' },
  { value: 'SUPPLIER', label: 'SUPPLIER (공급사)' },
  { value: 'BOTH', label: 'BOTH (양쪽)' },
];

export const BUSINESS_PARTNER_WRITE_CONFIG: MasterWriteConfig = {
  label: '거래처',
  createFields: [
    { key: 'code', label: '코드', kind: 'text', required: true },
    { key: 'name', label: '이름', kind: 'text', required: true },
    {
      key: 'partnerType',
      label: '거래처 유형',
      kind: 'select',
      required: true,
      options: PARTNER_TYPE_OPTIONS,
    },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
  updateFields: [
    { key: 'name', label: '이름', kind: 'text' },
    {
      key: 'partnerType',
      label: '거래처 유형',
      kind: 'select',
      options: PARTNER_TYPE_OPTIONS,
    },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
};
