import api from './api';
import { ChangeOwnPasswordRequest, LoginResponse, UpdateOwnProfileRequest, UserProfile } from '../types';

export const authService = {
  login: async (username: string, password: string): Promise<LoginResponse> => {
    const response = await api.post<LoginResponse>('/auth/login', {
      username,
      password,
    });
    return response.data;
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  },

  getCurrentUser: () => {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  },

  isAuthenticated: () => {
    return !!localStorage.getItem('token');
  },

  getProfile: async (): Promise<UserProfile> => {
    const response = await api.get<UserProfile>('/auth/me');
    return response.data;
  },

  updateProfile: async (data: UpdateOwnProfileRequest): Promise<UserProfile> => {
    const response = await api.put<UserProfile>('/auth/me', data);
    return response.data;
  },

  changeOwnPassword: async (data: ChangeOwnPasswordRequest): Promise<void> => {
    await api.put('/auth/me/password', data);
  },
};
