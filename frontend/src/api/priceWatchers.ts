import api from "../api";
import type { PriceWatcher } from "../types/priceWatchers";

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const priceWatchersApi = {
  watch: (productId: string) =>
    api.post<PriceWatcher>(`/products/${productId}/price-watch`),

  unwatch: (productId: string) =>
    api.delete(`/products/${productId}/price-watch`),

  list: (page = 0) =>
    api.get<PageResponse<PriceWatcher>>(`/price-watches?page=${page}`),
};
