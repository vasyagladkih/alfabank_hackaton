import type { components } from './schema';

export type UserCode = components['schemas']['UserCode'];
export type UserRole = components['schemas']['UserRole'];
export type UserProfile = components['schemas']['UserProfile'];
export type Session = components['schemas']['DemoLoginResponse'];
export type Application = components['schemas']['ApplicationResponse'];
export type ApplicationPage = components['schemas']['ApplicationPageResponse'];
export type ApplicationStatus = components['schemas']['ApplicationStatus'];
export type ApplicationInput = components['schemas']['CreateApplicationRequest'];
export type LoanPurpose = components['schemas']['LoanPurpose'];
export type StatusFilter = ApplicationStatus | 'ALL';
