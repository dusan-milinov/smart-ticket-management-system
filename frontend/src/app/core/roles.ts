export const ROLES = {
    USER: 'ROLE_USER',
    SUPPORT_AGENT: 'ROLE_SUPPORT_AGENT',
    ADMIN: 'ROLE_ADMIN',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export const ROLE_LABELS: Record<Role, string> = {
    ROLE_USER: 'User',
    ROLE_SUPPORT_AGENT: 'Support Agent',
    ROLE_ADMIN: 'Admin',
};

export function isRole(value: string | undefined): value is Role {
    return value !== undefined && value in ROLE_LABELS;
}
