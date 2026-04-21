export const userKeys = {
  all: ['user'] as const,
  profile: () => [...userKeys.all, 'profile'] as const,
  addresses: () => [...userKeys.all, 'addresses'] as const,
};
