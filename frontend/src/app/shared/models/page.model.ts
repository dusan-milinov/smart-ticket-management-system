export interface Page<T>{
    content: T[];
    totalElements: number;
    totalPages: number;
    currentPage: number;
    size: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}