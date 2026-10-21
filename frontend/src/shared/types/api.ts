export type Page<T> = {
  content: T[];
  page: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};
