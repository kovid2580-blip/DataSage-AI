import axios from 'axios';

const configuredBaseUrl = import.meta.env.VITE_API_URL?.trim();
const baseURL = configuredBaseUrl || (import.meta.env.DEV ? 'http://localhost:8080/api' : '/api');

const api = axios.create({
  baseURL,
});

export const uploadCsv = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const fetchData = () => api.get('/data');

export const askQuestion = (question) => api.post('/query', { question });

export const fetchHistory = () => api.get('/history');
