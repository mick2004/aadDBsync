package com.databricks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DBUser {
    public List<String> schemas;
    public String userName;

    public DBUser( String userName, String displayName) {
        this.schemas = Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:User");
        this.userName = userName;
        this.displayName = displayName;
    }

    public String displayName;
}
