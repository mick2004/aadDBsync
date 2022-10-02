package com.databricks;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

public class GroupMembers {
    @JsonProperty("@odata.context")
    public String odataContext;
    public ArrayList<GroupMember> value;
}
