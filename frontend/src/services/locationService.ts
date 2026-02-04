import api from './api';

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
    const response = await api.get('/locations/countries');
    return response.data;
  },

  async getProvincesByCountry(countryId: number): Promise<Province[]> {
    const response = await api.get(`/locations/countries/${countryId}/provinces`);
    return response.data;
  },
};
