package com.apm29.anxinju.faceserver;



public class CompareResult {
    private String userName;
    private String userId;
    private float similar;
    private int trackId;
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public CompareResult(String userName, float similar) {
        this.userName = userName;
        this.similar = similar;
    }

    public CompareResult(int id , String userId,String userName, float similar) {
        this.id = id;
        this.userName = userName;
        this.userId = userId;
        this.similar = similar;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public float getSimilar() {
        return similar;
    }

    public void setSimilar(float similar) {
        this.similar = similar;
    }

    public int getTrackId() {
        return trackId;
    }

    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }
}
