export interface Page<T>{
    content: T[];
    number: number;
    totalElements: number;
    totalPages: number;
    size: number;
    first: boolean;
    last: boolean;
    empty: boolean;
}