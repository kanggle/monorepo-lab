import type { MasterWriteConfig } from './MasterWriteDialog';

/**
 * Per-master field configs for the generic `MasterWriteDialog` (TASK-PC-FE-048).
 * Required markers mirror the producer `masterdata-api.md` / *Requests.java
 * bean-validation EXACTLY (verified against the live producer — a missing
 * required field 400s with `VALIDATION_ERROR`):
 *   - Employee create: employeeNumber, name, departmentId, costCenterId,
 *     jobGradeId all REQUIRED (an employee needs a full org placement).
 *   - JobGrade create: code, name, displayOrder REQUIRED.
 *   - CostCenter create: code, name, departmentId REQUIRED.
 *   - BusinessPartner create: code, name, partnerType, paymentTerms REQUIRED.
 * `effectiveFrom` is always optional (producer defaults today). UPDATE fields
 * are all optional (at-least-one enforced by the dialog).
 *
 * FK fields (departmentId / costCenterId / jobGradeId) render as dropdowns
 * sourced from the section lists — so creating e.g. an employee requires the
 * org masters (department / cost-center / job-grade) to exist first (the
 * producer enforces the same reference integrity).
 */

export const EMPLOYEE_WRITE_CONFIG: MasterWriteConfig = {
  label: '직원',
  createFields: [
    { key: 'employeeNumber', label: '사번', kind: 'text', required: true },
    { key: 'name', label: '이름', kind: 'text', required: true },
    { key: 'departmentId', label: '부서', kind: 'select', required: true, optionSource: 'departments' },
    { key: 'costCenterId', label: '비용센터', kind: 'select', required: true, optionSource: 'costCenters' },
    { key: 'jobGradeId', label: '직급', kind: 'select', required: true, optionSource: 'jobGrades' },
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
    { key: 'displayOrder', label: '표시 순서', kind: 'number', required: true },
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
    { key: 'departmentId', label: '부서', kind: 'select', required: true, optionSource: 'departments' },
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
    {
      key: 'paymentTerms',
      label: '결제 조건 (일수 + 결제수단)',
      kind: 'payment-terms',
      required: true,
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
    {
      key: 'paymentTerms',
      label: '결제 조건 (일수 + 결제수단)',
      kind: 'payment-terms',
    },
    { key: 'effectiveFrom', label: '유효 시작일 (선택)', kind: 'date' },
  ],
};
