import api from './api';
import { Course } from '../types';

export const courseService = {
  getAll: async (): Promise<Course[]> => {
    const response = await api.get<Course[]>('/courses');
    return response.data;
  },

  getById: async (id: number): Promise<Course> => {
    const response = await api.get<Course>(`/courses/${id}`);
    return response.data;
  },

  search: async (query: string): Promise<Course[]> => {
    const response = await api.get<Course[]>(`/courses/search?query=${query}`);
    return response.data;
  },

  create: async (data: Partial<Course>): Promise<Course> => {
    const response = await api.post<Course>('/courses', data);
    return response.data;
  },

  update: async (id: number, data: Partial<Course>): Promise<Course> => {
    const response = await api.put<Course>(`/courses/${id}`, data);
    return response.data;
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/courses/${id}`);
  },

  getTees: async (courseId: number) => {
    const response = await api.get(`/courses/${courseId}/tees`);
    return response.data;
  },

  addTee: async (courseId: number, tee: any) => {
    const response = await api.post(`/courses/${courseId}/tees`, tee);
    return response.data;
  },

  updateTee: async (teeId: number, tee: any) => {
    const response = await api.put(`/courses/tees/${teeId}`, tee);
    return response.data;
  },

  deactivateTee: async (teeId: number) => {
    await api.delete(`/courses/tees/${teeId}`);
  },

  getHoles: async (courseId: number) => {
    const response = await api.get(`/courses/${courseId}/holes`);
    return response.data;
  },

  saveHole: async (courseId: number, hole: any) => {
    const response = await api.post(`/courses/${courseId}/holes`, hole);
    return response.data;
  },
};
