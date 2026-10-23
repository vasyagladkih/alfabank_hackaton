import type { components } from './schema';

export type UserCode = components['schemas']['UserCode'];
export type UserRole = components['schemas']['UserRole'];
export type UserProfile = components['schemas']['UserProfile'];
export type Session = components['schemas']['DemoLoginResponse'];
export type Application = components['schemas']['ApplicationResponse'];
export type ApplicationStatus = components['schemas']['ApplicationStatus'];
export type ApplicationInput = components['schemas']['CreateApplicationRequest'];
export type LoanPurpose = components['schemas']['LoanPurpose'];
export type StatusFilter = ApplicationStatus | 'ALL';
export type EmployeeDossier = components['schemas']['EmployeeDossier'];
export type EmployeeDecisionRequest = components['schemas']['EmployeeDecisionRequest'];
export type EmployeeDecisionType = components['schemas']['EmployeeDecisionType'];
export type OfferResponseRequest = components['schemas']['OfferResponseRequest'];
export type DossierSource = components['schemas']['DossierSource'];
