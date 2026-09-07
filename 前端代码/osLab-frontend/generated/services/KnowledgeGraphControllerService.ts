/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AddRelationRequest } from '../models/AddRelationRequest';
import type { BaseResponse_EntityNode_ } from '../models/BaseResponse_EntityNode_';
import type { BaseResponse_GraphDataDTO_ } from '../models/BaseResponse_GraphDataDTO_';
import type { BaseResponse_List_string_ } from '../models/BaseResponse_List_string_';
import type { BaseResponse_string_ } from '../models/BaseResponse_string_';
import type { CypherRequest } from '../models/CypherRequest';
import type { DeleteNodeRequest } from '../models/DeleteNodeRequest';
import type { NodeDTO } from '../models/NodeDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class KnowledgeGraphControllerService {
    /**
     * addKnowledgeGraphNode
     * @param nodeDto nodeDTO
     * @returns BaseResponse_EntityNode_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static addKnowledgeGraphNodeUsingPost(
        nodeDto: NodeDTO,
    ): CancelablePromise<BaseResponse_EntityNode_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/knowledge_graph/addNode',
            body: nodeDto,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * addKnowledgeGraphRelation
     * @param addRelationRequest addRelationRequest
     * @returns BaseResponse_string_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static addKnowledgeGraphRelationUsingPost(
        addRelationRequest: AddRelationRequest,
    ): CancelablePromise<BaseResponse_string_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/knowledge_graph/addRelation',
            body: addRelationRequest,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * deleteByName
     * @param deleteNodeRequest deleteNodeRequest
     * @returns BaseResponse_string_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static deleteByNameUsingPost(
        deleteNodeRequest: DeleteNodeRequest,
    ): CancelablePromise<BaseResponse_string_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/knowledge_graph/delete',
            body: deleteNodeRequest,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * execute
     * @param cypherRequest cypherRequest
     * @returns BaseResponse_string_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static executeUsingPost(
        cypherRequest: CypherRequest,
    ): CancelablePromise<BaseResponse_string_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/knowledge_graph/execute',
            body: cypherRequest,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * getKnowledgeGraphData
     * @param name name
     * @returns BaseResponse_GraphDataDTO_ OK
     * @throws ApiError
     */
    public static getKnowledgeGraphDataUsingGet(
        name?: string,
    ): CancelablePromise<BaseResponse_GraphDataDTO_> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/knowledge_graph/get',
            query: {
                'name': name,
            },
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * getAllKnowledgeGraphData
     * @returns BaseResponse_GraphDataDTO_ OK
     * @throws ApiError
     */
    public static getAllKnowledgeGraphDataUsingGet(): CancelablePromise<BaseResponse_GraphDataDTO_> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/knowledge_graph/getAll',
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * getKnowledgeGraphDataByList
     * @param names names
     * @returns BaseResponse_GraphDataDTO_ OK
     * @returns any Created
     * @throws ApiError
     */
    public static getKnowledgeGraphDataByListUsingPost(
        names: Array<string>,
    ): CancelablePromise<BaseResponse_GraphDataDTO_ | any> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/knowledge_graph/getMultiple',
            body: names,
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
    /**
     * getAllKnowledgeGraphName
     * @returns BaseResponse_List_string_ OK
     * @throws ApiError
     */
    public static getAllKnowledgeGraphNameUsingGet(): CancelablePromise<BaseResponse_List_string_> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/knowledge_graph/getNames',
            errors: {
                401: `Unauthorized`,
                403: `Forbidden`,
                404: `Not Found`,
            },
        });
    }
}
