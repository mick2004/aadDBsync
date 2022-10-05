package com.databricks;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

public class DBUsersResp {
    public int totalResults;
    public int startIndex;
    public int itemsPerPage;
    public ArrayList<String> schemas;
    @JsonProperty("Resources")
    public ArrayList<DBUserResp> resources;

}
