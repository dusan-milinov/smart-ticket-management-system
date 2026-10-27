import { Service, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, finalize, firstValueFrom, map, of, tap } from 'rxjs';
import { API_BASE_URL } from './api';

export interface CurrentUser {
    email: string;
    role: string;
}

@Service()
export class AuthService {

    private readonly http = inject(HttpClient);

    private readonly _user = signal<CurrentUser | null>(null);
    readonly user = this._user.asReadonly();

    login(email:string, password:string) {
        return this.http.post<void>(`${API_BASE_URL}/auth/login`, { email, password }, {withCredentials: true});
    }

    register(email: string, password: string) {
        return this.http.post<void>(`${API_BASE_URL}/auth/register`, { email, password });
    }

    getCurrentUser() {
        return this.http.get<CurrentUser>(`${API_BASE_URL}/users/me`, {withCredentials: true})
            .pipe(tap((user) => this._user.set(user)));
    }

    // The HttpOnly cookie is invisible to JS, so /users/me tells us whether a session exists.
    restoreSession(): Promise<void> {
        return firstValueFrom(
            this.getCurrentUser().pipe(
                catchError(() => of(null)),
                map(() => undefined),
            ),
        );
    }

    // The server expires the cookie; the local state is cleared whether or not the call succeeds.
    logout() {
        return this.http.post<void>(`${API_BASE_URL}/auth/logout`, {}, {withCredentials: true})
            .pipe(finalize(() => this._user.set(null)));
    }
}
