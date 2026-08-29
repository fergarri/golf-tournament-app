import api from './api';
import { Course, ImportHandicapConversionResponse, PreviewHandicapImportResponse, TeeHandicapTable } from '../types';

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

  deleteTee: async (teeId: number) => {
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

  getHandicapConversions: async (courseId: number): Promise<TeeHandicapTable[]> => {
    const response = await api.get<TeeHandicapTable[]>(`/courses/${courseId}/handicap-conversions`);
    return response.data;
  },

  previewHandicapConversions: async (
    courseId: number,
    file: File
  ): Promise<PreviewHandicapImportResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<PreviewHandicapImportResponse>(
      `/courses/${courseId}/handicap-conversions/preview`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  },

  importHandicapConversions: async (
    courseId: number,
    file: File,
    teeIds: number[],
    createMissing = false
  ): Promise<ImportHandicapConversionResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    teeIds.forEach((id) => formData.append('teeIds', String(id)));
    formData.append('createMissing', String(createMissing));
    const response = await api.post<ImportHandicapConversionResponse>(
      `/courses/${courseId}/handicap-conversions/import`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  },

  previewHoleDistances: async (
    courseId: number,
    file: File
  ): Promise<PreviewHandicapImportResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<PreviewHandicapImportResponse>(
      `/courses/${courseId}/hole-distances/preview`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  },

  importHoleDistances: async (
    courseId: number,
    file: File,
    teeIds: number[],
    createMissing = false
  ): Promise<ImportHandicapConversionResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    teeIds.forEach((id) => formData.append('teeIds', String(id)));
    formData.append('createMissing', String(createMissing));
    const response = await api.post<ImportHandicapConversionResponse>(
      `/courses/${courseId}/hole-distances/import`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  },
};
