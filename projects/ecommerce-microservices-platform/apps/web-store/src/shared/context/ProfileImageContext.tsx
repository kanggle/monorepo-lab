'use client';

import { createContext, useCallback, useContext, useState } from 'react';

interface ProfileImageContextValue {
  imageUrl: string;
  setImageUrl: (url: string) => void;
}

const ProfileImageContext = createContext<ProfileImageContextValue>({
  imageUrl: '',
  setImageUrl: () => {},
});

export function ProfileImageProvider({ children }: { children: React.ReactNode }) {
  const [imageUrl, setImageUrlState] = useState('');

  const setImageUrl = useCallback((url: string) => {
    setImageUrlState(url);
  }, []);

  return (
    <ProfileImageContext.Provider value={{ imageUrl, setImageUrl }}>
      {children}
    </ProfileImageContext.Provider>
  );
}

export function useProfileImage() {
  return useContext(ProfileImageContext);
}
