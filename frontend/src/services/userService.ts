import api from './api';
import { UserDetail, CreateUserRequest, UpdateUserRequest, ChangePasswordRequest } from '../types';

export const userService = {
  getAll: async (): Promise<UserDetail[]> => {
    const response = await api.get<UserDetail[]>('/users');
    return response.data;
  },

  getById: async (id: number): Promise<UserDetail> => {
    const response = await api.get<UserDetail>(`/users/${id}`);
    return response.data;
  },

  create: async (data: CreateUserRequest): Promise<UserDetail> => {
    const response = await api.post<UserDetail>('/users', data);
    return response.data;
  },

  update: async (id: number, data: UpdateUserRequest): Promise<UserDetail> => {
    const response = await api.put<UserDetail>(`/users/${id}`, data);
    return response.data;
  },

  changePassword: async (id: number, data: ChangePasswordRequest): Promise<void> => {
    await api.put(`/users/${id}/password`, data);
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/users/${id}`);
  },
};
