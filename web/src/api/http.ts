import axios from 'axios';

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
  withCredentials: true,
});
