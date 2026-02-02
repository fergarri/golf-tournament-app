import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export interface Country {
  id: number;
  nombre: string;
  codigoIso: string;
}

export interface Province {
  id: number;
  countryId: number;
  nombre: string;
}

export const locationService = {
  async getCountries(): Promise<Country[]> {
    const response = await axios.get(`${API_BASE_URL}/locations/countries`);
    return response.data;
  },

  async getProvincesByCountry(countryId: number): Promise<Province[]> {
    const response = await axios.get(`${API_BASE_URL}/locations/countries/${countryId}/provinces`);
    return response.data;
  },
};
