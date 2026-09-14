package com.ewaste.formalization.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface EWasteApiService {

    @POST("api/sync/batches")
    suspend fun syncBatches(
        @Body request: SyncBatchesRequest
    ): Response<SyncBatchesResponse>

    @GET("api/recyclers")
    suspend fun getRecyclers(): Response<List<RecyclerSyncDto>>
}
