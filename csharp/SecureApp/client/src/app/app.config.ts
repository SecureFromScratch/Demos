import {
  ApplicationConfig,
  provideZoneChangeDetection,
  importProvidersFrom
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { credentialsInterceptor } from './interceptors/credentials.interceptor';


import { HttpClient, provideHttpClient, withInterceptors, HttpClientXsrfModule, withInterceptorsFromDi } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { csrfInterceptor } from './interceptors/csrf.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([credentialsInterceptor, csrfInterceptor ]),            
    ),

    // This line makes *ngIf/*ngFor and [(ngModel)] available app-wide
    importProvidersFrom(CommonModule, FormsModule, HttpClient )
  ]
};


