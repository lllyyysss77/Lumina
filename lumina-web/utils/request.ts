/**
 * API Request Utility Class
 * Encapsulates fetch API with common features like timeout, error handling, and type support.
 */

export interface RequestConfig extends RequestInit {
  baseURL?: string;
  params?: Record<string, string | number | boolean | undefined | null>;
  timeout?: number; // Timeout in milliseconds
  skipAuth?: boolean; // Skip adding Authorization header
}

export interface ApiResponse<T = any> {
  data: T;
  status: number;
  headers: Headers;
}

export class RequestError extends Error {
  public status: number;
  public statusText: string;
  public data: any;

  constructor(status: number, statusText: string, data: any) {
    super(`Request failed with status ${status}: ${statusText}`);
    this.name = 'RequestError';
    this.status = status;
    this.statusText = statusText;
    this.data = data;
  }
}

class HttpClient {
  private defaultConfig: RequestConfig;

  constructor(config: RequestConfig = {}) {
    this.defaultConfig = {
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      timeout: 15000, // Default 15s timeout
      ...config,
    };
  }

  /**
   * Core request method
   */
  public async request<T>(url: string, config: RequestConfig = {}): Promise<T> {
    // Merge config
    const { baseURL, params, timeout, headers, skipAuth, ...customConfig } = config;
    
    // Get Token from LocalStorage
    const token = localStorage.getItem('lumina_token');
    // Only add Authorization header if token exists AND skipAuth is not true
    const authHeader = (token && !skipAuth) ? { 'Authorization': `Bearer ${token}` } : {};

    const mergedHeaders = {
      ...this.defaultConfig.headers,
      ...authHeader,
      ...headers,
    } as Record<string, string>;

    const finalBaseURL = baseURL ?? this.defaultConfig.baseURL ?? '';
    let finalURL = `${finalBaseURL}${url}`;

    // Handle Query Parameters
    if (params) {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          searchParams.append(key, String(value));
        }
      });
      const queryString = searchParams.toString();
      if (queryString) {
        finalURL += (finalURL.includes('?') ? '&' : '?') + queryString;
      }
    }

    // Handle Timeout
    const fetchTimeout = timeout ?? this.defaultConfig.timeout ?? 15000;
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), fetchTimeout);

    try {
      const response = await fetch(finalURL, {
        ...this.defaultConfig,
        ...customConfig,
        headers: mergedHeaders,
        signal: controller.signal,
      });

      clearTimeout(id);

      // Handle HTTP Errors
      if (!response.ok) {
        let errorData;
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
           try {
             errorData = await response.json();
           } catch {
             errorData = await response.text();
           }
        } else {
           errorData = await response.text();
        }

        // Handle 401 Unauthorized
        // If the request was authenticated (not skipAuth) and failed with 401,
        // it means the session is invalid/expired.
        if (response.status === 401 && !skipAuth) {
          localStorage.removeItem('lumina_token');
          localStorage.removeItem('lumina_user');
          // Reloading the page will trigger App.tsx to check auth state and render Login component
          window.location.reload();
          // Throw error to stop current execution flow
          throw new RequestError(response.status, 'Session Expired', errorData);
        }
        
        throw new RequestError(response.status, response.statusText, errorData);
      }

      // Handle 204 No Content
      if (response.status === 204) {
        return {} as T;
      }

      // Parse Response
      const responseType = response.headers.get('content-type');
      if (responseType && responseType.includes('application/json')) {
        return await response.json();
      }
      
      return (await response.text()) as unknown as T;

    } catch (error: any) {
      clearTimeout(id);
      if (error.name === 'AbortError') {
        throw new Error(`Request timeout of ${fetchTimeout}ms exceeded`);
      }
      
      // Log network errors for debugging
      console.error('API Request Failed:', error);
      
      // Re-throw the error to be handled by the caller
      throw error;
    }
  }

  // Convenience Methods

  public get<T>(url: string, config?: RequestConfig): Promise<T> {
    return this.request<T>(url, { ...config, method: 'GET' });
  }

  public post<T>(url: string, data?: any, config?: RequestConfig): Promise<T> {
    return this.request<T>(url, {
      ...config,
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  public put<T>(url: string, data?: any, config?: RequestConfig): Promise<T> {
    return this.request<T>(url, {
      ...config,
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  public patch<T>(url: string, data?: any, config?: RequestConfig): Promise<T> {
    return this.request<T>(url, {
      ...config,
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  }

  public delete<T>(url: string, config?: RequestConfig): Promise<T> {
    return this.request<T>(url, { ...config, method: 'DELETE' });
  }
}

// Export a singleton instance with default configuration
export const api = new HttpClient({
  baseURL: 'https://lumina.jojoz.cn/api/v1',
});

export default HttpClient;