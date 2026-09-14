package com.example.revroom.data.remote;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @GET("api/design/projects")
    Call<ProjectHistoryResponse> getProjects(
        @Header("user-id") String userId,
        @Query("page") int page,
        @Query("pageSize") int pageSize
    );

    @DELETE("api/design/{designId}")
    Call<DeleteResponse> deleteProject(
        @Header("user-id") String userId,
        @Path("designId") String designId
    );
}
