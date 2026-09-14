package com.example.revroom.data.remote;

import com.google.gson.annotations.SerializedName;

public class DeleteResponse {
    @SerializedName("message")
    private String message;

    public DeleteResponse() {
    }

    public DeleteResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
