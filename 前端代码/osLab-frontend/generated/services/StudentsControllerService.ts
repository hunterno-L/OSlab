/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BaseResponse_boolean_ } from '../models/BaseResponse_boolean_';
import type { BaseResponse_Page_StudentVO_ } from '../models/BaseResponse_Page_StudentVO_';
import type { BaseResponse_StudentVO_ } from '../models/BaseResponse_StudentVO_';
import type { Resource } from '../models/Resource';
import type { StudentAddRequest } from '../models/StudentAddRequest';
import type { StudentQueryRequest } from '../models/StudentQueryRequest';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class StudentsControllerService {
    /**
     * addStudents
     * @param studentAddRequest studentAddRequest
     * @returns BaseResponse_StudentVO_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static addStudentsUsingPost(
        studentAddRequest: StudentAddRequest,
    ): CancelablePromise<BaseResponse_StudentVO_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/student/add',
            body: studentAddRequest,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * uploadStudentsFile
     * @param file file
     * @returns Resource OK
     * @returns any Created
     * @throws ApiError
     */
    public static uploadStudentsFileUsingPost(
      file: Blob,
    ): Promise<void> {
        const formData = new FormData();
        formData.append('file', file);

        return new Promise((resolve, reject) => {
            fetch(`${OpenAPI.BASE}/api/student/batchAdd`, {
                method: 'POST',
                body: formData,
            })
              .then(async response => {
                  if (!response.ok) {
                      const error = await response.json().catch(() => ({}));
                      throw new Error(error.message || '请求失败');
                  }
                  return response.blob(); // 确保获取Blob
              })
              .then(blob => {
                  const url = window.URL.createObjectURL(blob);
                  const link = document.createElement('a');
                  link.href = url;
                  link.setAttribute('download', 'students.xlsx');
                  document.body.appendChild(link);
                  link.click();
                  document.body.removeChild(link);
                  window.URL.revokeObjectURL(url);
                  resolve();
              })
              .catch(reject);
        });
    }
    /**
     * deleteStudent
     * @param id id
     * @returns BaseResponse_boolean_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static deleteStudentUsingPost(
        id?: number,
    ): CancelablePromise<BaseResponse_boolean_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/student/delete',
            query: {
                'id': id,
            },
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * resetPassword
     * @param id id
     * @returns BaseResponse_StudentVO_ OK
     * @throws ApiError
     */
    public static resetPasswordUsingPost(
        id?: number,
    ): CancelablePromise<BaseResponse_StudentVO_> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/student/reset-password',
            query: {
                'id': id,
            },
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * listStudentByPage
     * @param studentQueryRequest studentQueryRequest
     * @returns BaseResponse_Page_StudentVO_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static listStudentByPageUsingPost(
        studentQueryRequest: StudentQueryRequest,
    ): CancelablePromise<BaseResponse_Page_StudentVO_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/student/list/page',
            body: studentQueryRequest,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
}
