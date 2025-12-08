import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';

export interface MeResponse {
  userName: string;
  roles: string[];
}

export interface LoginResponse {
  token: string;
  user: MeResponse;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
 private readonly AUTH_KEY = 'isLoggedIn'; 

  private baseUrl = '/bff/account';  

  constructor(private http: HttpClient) { }

  isFirstUser(): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/is-first-user`);
  }

  setup(userName: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/setup`, { userName, password });
  }

  register(userName: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/register`, { userName, password });
  }

  login(userName: string, password: string): Observable<MeResponse> {
    return this.http.post<MeResponse>(
      `${this.baseUrl}/login`,
      { userName, password },
      { withCredentials: true }
    ).pipe(
      tap(() => {
        // just mark as logged in for the guard
        localStorage.setItem(this.AUTH_KEY, 'true');
      })
    );
  }


  logout(): void {
    localStorage.removeItem(this.AUTH_KEY);    
  }

  // ⚠ DEMO ONLY – NOT REAL SECURITY
  // This flag is trivially bypassed and is here just to demonstrate why
  // client-side checks are not enough.
  isLoggedIn(): boolean {
    return localStorage.getItem(this.AUTH_KEY) === 'true';
  }

  me(): Observable<MeResponse | null> {
    return this.http.get<MeResponse>(`${this.baseUrl}/me`).pipe(
      map(x => x ?? null)
    );
  }
  

  private setToken(token: string): void {    
    localStorage.setItem(this.AUTH_KEY, 'true');
  }

  private clearToken(): void {
    localStorage.removeItem(this.AUTH_KEY);
  }
}