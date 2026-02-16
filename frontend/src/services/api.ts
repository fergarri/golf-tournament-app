import axios from 'axios';
import { isTokenExpired } from '../utils/jwtUtils';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    
    // Verificar si el token está expirado ANTES de hacer la petición
    if (token && isTokenExpired(token)) {
      console.log('⏰ Token expirado detectado - redirigiendo al login');
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
      return Promise.reject(new Error('Token expirado'));
    }
    
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    
    // Evitar bucles infinitos si ya estamos en login
    if (window.location.pathname === '/login') {
      return Promise.reject(error);
    }

    // 401 o 403 indican problemas de autenticación
    // (El interceptor de request ya debería manejar tokens expirados,
    // pero esto es un respaldo por si el servidor invalida el token)
    if (status === 401 || status === 403) {
      console.log(`⚠️ Error ${status} detectado - redirigiendo al login`);
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    return Promise.reject(error);
  }
);

export default api;
